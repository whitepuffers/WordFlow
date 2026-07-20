package com.wordflow.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.components.CalendarHeatmap
import com.wordflow.app.ui.components.EmptyState
import com.wordflow.app.ui.components.MasteryPieChart
import com.wordflow.app.ui.components.SectionCard
import com.wordflow.app.ui.components.StatItem
import com.wordflow.app.ui.components.TrendLineChart
import com.wordflow.app.ui.theme.Error400
import com.wordflow.app.ui.theme.Orange500
import com.wordflow.app.ui.theme.Success500
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsRoute(onGoStudy: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: StatsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            StatsViewModel(
                container.settingsRepository,
                container.wordRepository,
                container.studyRepository
            )
        }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(state = state, onGoStudy = onGoStudy)
}

@Composable
fun StatsScreen(state: StatsUiState, onGoStudy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "学习进度",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        if (!state.loading && !state.hasData) {
            EmptyState(
                emoji = "📊",
                title = "还没有学习数据",
                subtitle = "背几个单词后，这里会出现漂亮的统计图表",
                actionLabel = "去学习",
                onAction = onGoStudy
            )
            return@Column
        }

        // ---- 顶部数字 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "${state.totalLearned}", label = "累计学习")
                StatItem(value = "${state.masteredCount}", label = "已掌握", valueColor = Success500)
                StatItem(value = "${state.streak}", label = "连续打卡", valueColor = Orange500)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 折线图：近 7 / 30 天 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                var rangeIndex by remember { mutableIntStateOf(0) }
                val ranges = listOf(7, 30)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("每日学习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow {
                        ranges.forEachIndexed { index, days ->
                            SegmentedButton(
                                selected = rangeIndex == index,
                                onClick = { rangeIndex = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size)
                            ) {
                                Text("${days}天")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val days = ranges[rangeIndex]
                val formatter = remember { DateTimeFormatter.ofPattern("M/d") }
                val chartData = remember(state.dailyCounts, days) {
                    (days - 1 downTo 0).map { offset ->
                        val date = LocalDate.now().minusDays(offset.toLong())
                        val label = date.format(formatter)
                        label to (state.dailyCounts[date.toEpochDay()] ?: 0)
                    }
                }
                TrendLineChart(data = chartData)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 日历热力图 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("学习热力", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "最近 18 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                CalendarHeatmap(
                    counts = state.dailyCounts,
                    todayEpochDay = state.todayEpochDay,
                    weeks = 18
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 掌握度饼图 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("单词掌握度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                MasteryPieChart(
                    data = listOf(
                        Triple("新学", state.newCount, MaterialTheme.colorScheme.primary),
                        Triple("学习中", state.learningCount, Orange500),
                        Triple("已掌握", state.masteredCount, Success500)
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 成就墙 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val unlockedCount = state.achievements.count { it.unlocked }
                Text(
                    "成就徽章（$unlockedCount/${state.achievements.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                state.achievements.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { item ->
                            AchievementBadge(item = item, modifier = Modifier.weight(1f))
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AchievementBadge(item: AchievementUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.alpha(if (item.unlocked) 1f else 0.45f),
        shape = RoundedCornerShape(14.dp),
        color = if (item.unlocked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (item.unlocked) item.def.emoji else "🔒",
                fontSize = 28.sp
            )
            Spacer(Modifier.padding(start = 10.dp))
            Column {
                Text(
                    item.def.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.def.desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
