package com.wordflow.app.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflow.app.data.db.entity.WordEntity
import com.wordflow.app.ui.theme.Error500
import com.wordflow.app.ui.theme.Orange500
import com.wordflow.app.ui.theme.Success500
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * 可滑动单词卡片：
 *  - 点击：3D 翻转（正面单词/背面释义）
 *  - 右滑：认识    左滑：不认识    上滑：收藏
 *  - 拖动时卡片倾斜 + 颜色渐变 + 方向标签
 */
@Composable
fun SwipeFlashCard(
    word: WordEntity,
    isFavorite: Boolean,
    onMark: (known: Boolean) -> Unit,
    onFavorite: () -> Unit,
    onSpeak: () -> Unit,
    haptic: (HapticFeedbackType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var flipped by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }

    val flipRotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(450),
        label = "flip"
    )

    val thresholdX = with(density) { 110.dp.toPx() }
    val thresholdY = with(density) { 110.dp.toPx() }
    var crossedX by remember { mutableStateOf(0) }   // -1/0/1，用于越界震动
    var crossedUp by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .pointerInput(flying) {
                if (flying) return@pointerInput
                detectTapGestures {
                    flipped = !flipped
                    haptic(HapticFeedbackType.TextHandleMove)
                }
            }
            .pointerInput(flying) {
                if (flying) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        crossedX = 0
                        crossedUp = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                        // 越过阈值瞬间震动一次
                        val newCross = when {
                            offsetX.value > thresholdX -> 1
                            offsetX.value < -thresholdX -> -1
                            else -> 0
                        }
                        if (newCross != 0 && newCross != crossedX) {
                            haptic(HapticFeedbackType.GestureThresholdActivate)
                        }
                        crossedX = newCross
                        val up = offsetY.value < -thresholdY
                        if (up && !crossedUp) haptic(HapticFeedbackType.GestureThresholdActivate)
                        crossedUp = up
                    },
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.value > thresholdX -> {
                                    flying = true
                                    haptic(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(size.width * 1.6f, tween(220))
                                    onMark(true)
                                }

                                offsetX.value < -thresholdX -> {
                                    flying = true
                                    haptic(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(-size.width * 1.6f, tween(220))
                                    onMark(false)
                                }

                                offsetY.value < -thresholdY -> {
                                    onFavorite()
                                    haptic(HapticFeedbackType.LongPress)
                                    launch { offsetX.animateTo(0f, spring()) }
                                    offsetY.animateTo(0f, spring())
                                }

                                else -> {
                                    launch { offsetX.animateTo(0f, spring()) }
                                    launch { offsetY.animateTo(0f, spring()) }
                                }
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value * 0.7f
                rotationZ = offsetX.value / 28f
                rotationY = flipRotation
                cameraDistance = 14f * density.density
            }
    ) {
        Card(
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxSize()
        ) {
            if (flipRotation <= 90f) {
                CardFront(word = word, isFavorite = isFavorite, onSpeak = onSpeak, haptic = haptic)
            } else {
                // 背面内容再翻转 180°，抵消镜像
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    CardBack(word = word)
                }
            }
        }

        // 拖动方向颜色渐变
        val overlayColor = when {
            offsetX.value > 40f -> Success500.copy(alpha = min(0.30f, offsetX.value / 700f))
            offsetX.value < -40f -> Error500.copy(alpha = min(0.30f, abs(offsetX.value) / 700f))
            offsetY.value < -40f -> Orange500.copy(alpha = min(0.30f, abs(offsetY.value) / 700f))
            else -> Color.Transparent
        }
        if (overlayColor != Color.Transparent) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(overlayColor)
            )
        }

        // 方向标签
        when {
            offsetX.value > 60f -> DirectionLabel("认识 ✓", Success500, Modifier.align(Alignment.TopStart))
            offsetX.value < -60f -> DirectionLabel("不认识 ✗", Error500, Modifier.align(Alignment.TopEnd))
            offsetY.value < -60f -> DirectionLabel("收藏 ★", Orange500, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun DirectionLabel(text: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun CardFront(
    word: WordEntity,
    isFavorite: Boolean,
    onSpeak: () -> Unit,
    haptic: (HapticFeedbackType) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 收藏角标
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "收藏状态",
            tint = if (isFavorite) Orange500 else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(26.dp)
        )
        // 发音按钮
        IconButton(
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onSpeak()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = "播放发音",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                word.word,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            if (word.phonetic.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    word.phonetic,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "点击卡片查看释义",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun CardBack(word: WordEntity) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            word.word,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            word.meaning,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (word.example.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                word.example,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (word.exampleCn.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    word.exampleCn,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 叠在下一张的静态背景卡片 */
@Composable
fun NextCardPlaceholder(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {}
}

/** 底部手势操作提示 */
@Composable
fun GestureHints(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Text("← 不认识", color = Error500, style = MaterialTheme.typography.labelLarge)
        Text("↑ 收藏", color = Orange500, style = MaterialTheme.typography.labelLarge)
        Text("认识 →", color = Success500, style = MaterialTheme.typography.labelLarge)
    }
}
