package com.wordflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 折线图：data 为 (x轴标签, 数值) 列表。
 * 数据全为 0 时也正常显示坐标网格。
 */
@Composable
fun TrendLineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = lineColor.copy(alpha = 0.12f)

    // 标签太密时隔几个显示一个
    val labelStep = max(1, data.size / 7)

    Canvas(modifier = modifier.fillMaxWidth().height(190.dp)) {
        val leftPad = 16f
        val rightPad = 16f
        val topPad = 24f
        val bottomPad = 52f
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val maxV = max(4, data.maxOf { it.second })

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val valuePaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 24f
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }

        // 3 条水平网格线 + 纵轴刻度
        for (i in 0..2) {
            val y = topPad + chartH * i / 2f
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 2f)
            val v = (maxV * (2 - i) / 2f).toInt()
            drawContext.canvas.nativeCanvas.drawText("$v", leftPad, y - 8f, valuePaint)
        }

        val stepX = if (data.size > 1) chartW / (data.size - 1) else 0f
        fun pointAt(index: Int): Offset {
            val x = leftPad + stepX * index
            val y = topPad + chartH * (1f - data[index].second.toFloat() / maxV)
            return Offset(x, y)
        }

        // 渐变填充区域
        if (data.size > 1) {
            val fillPath = Path()
            fillPath.moveTo(pointAt(0).x, topPad + chartH)
            for (i in data.indices) fillPath.lineTo(pointAt(i).x, pointAt(i).y)
            fillPath.lineTo(pointAt(data.size - 1).x, topPad + chartH)
            fillPath.close()
            drawPath(fillPath, fillColor)
        }

        // 折线
        if (data.size > 1) {
            val linePath = Path()
            data.indices.forEach { i ->
                val p = pointAt(i)
                if (i == 0) linePath.moveTo(p.x, p.y) else linePath.lineTo(p.x, p.y)
            }
            drawPath(linePath, lineColor, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }

        // 数据点 + x 轴标签
        data.forEachIndexed { i, pair ->
            val p = pointAt(i)
            drawCircle(color = lineColor, radius = 6f, center = p)
            drawCircle(color = Color.White, radius = 2.5f, center = p)
            if (i % labelStep == 0 || i == data.size - 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    pair.first, p.x, size.height - 14f, labelPaint
                )
            }
        }
    }
}

/**
 * GitHub 风格日历热力图。
 * @param counts  key 为 epochDay，value 为当天学习数
 * @param weeks   显示最近多少周（列数）
 */
@Composable
fun CalendarHeatmap(
    counts: Map<Long, Int>,
    todayEpochDay: Long,
    weeks: Int = 18,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.primary
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val maxCount = max(1, counts.values.maxOrNull() ?: 1)
    val startDay = todayEpochDay - (weeks * 7 - 1)

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { dayOfWeek ->
                        val day = startDay + week * 7 + dayOfWeek
                        val count = counts[day] ?: 0
                        val color = when {
                            day > todayEpochDay -> Color.Transparent
                            count == 0 -> emptyColor
                            else -> baseColor.copy(alpha = 0.25f + 0.75f * (count.toFloat() / maxCount))
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(color, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 图例
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                val c = if (ratio == 0f) emptyColor else baseColor.copy(alpha = 0.25f + 0.75f * ratio)
                Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(2.dp))
            }
            Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 掌握度饼图 + 图例 */
@Composable
fun MasteryPieChart(
    data: List<Triple<String, Int, Color>>, // (标签, 数量, 颜色)
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeInset = 0f
            val diameter = size.minDimension - strokeInset
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            data.forEach { (_, count, color) ->
                val sweep = 360f * count / total
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )
                }
                startAngle += sweep
            }
            // 中心镂空成甜甜圈
            drawCircle(color = Color.Transparent)
        }
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { (label, count, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$label  $count（${(count * 100 / total)}%）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = labelColor
                    )
                }
            }
        }
    }
}
