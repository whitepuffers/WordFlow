package com.wordflow.app.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordflow.app.domain.AchievementDef
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.LocalSoundEnabled
import com.wordflow.app.ui.components.EmptyState
import com.wordflow.app.ui.components.LoadingState
import com.wordflow.app.ui.components.StatItem
import com.wordflow.app.ui.rememberAppHaptics
import com.wordflow.app.tts.SoundPlayer
import kotlinx.coroutines.delay

@Composable
fun StudyRoute(mode: DeckMode, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: StudyViewModel = viewModel(
        key = "study-${mode.raw}",
        factory = viewModelFactory {
            initializer {
                StudyViewModel(
                    mode,
                    container.wordRepository,
                    container.studyRepository,
                    container.settingsRepository
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberAppHaptics()
    val soundEnabled = LocalSoundEnabled.current

    // 成就解锁：播放提示音 + 震动
    LaunchedEffect(state.newAchievements) {
        if (state.newAchievements.isNotEmpty()) {
            if (soundEnabled) SoundPlayer.celebrate()
            haptic(HapticFeedbackType.LongPress)
        }
    }

    StudyScreen(
        state = state,
        title = if (mode == DeckMode.NEW) "学习新词" else "快速复习",
        onExit = onExit,
        onMark = viewModel::mark,
        onFavorite = viewModel::toggleFavorite,
        onSpeak = { word -> if (soundEnabled) container.ttsManager.speak(word) },
        onRestart = viewModel::restart,
        onDismissAchievements = viewModel::dismissAchievements,
        haptic = haptic
    )
}

@Composable
fun StudyScreen(
    state: StudyUiState,
    title: String,
    onExit: () -> Unit,
    onMark: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onSpeak: (String) -> Unit,
    onRestart: () -> Unit,
    onDismissAchievements: () -> Unit,
    haptic: (HapticFeedbackType) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingState(message = "正在准备单词…")

            state.empty -> EmptyState(
                emoji = state.emptyEmoji,
                title = state.emptyTitle,
                subtitle = state.emptyMessage,
                actionLabel = "返回首页",
                onAction = onExit
            )

            else -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                // 顶部：关闭 + 进度
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onExit()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "退出学习")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (state.total == 0) 0f
                                else state.index.toFloat() / state.total
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${minOf(state.index + 1, state.total)}/${state.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 卡片堆
                val current = state.current
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.next != null) {
                        NextCardPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(430.dp)
                                .scale(0.96f)
                        )
                    }
                    if (current != null) {
                        androidx.compose.runtime.key(current.id) {
                            SwipeFlashCard(
                                word = current,
                                isFavorite = current.id in state.favoriteIds,
                                onMark = onMark,
                                onFavorite = onFavorite,
                                onSpeak = { onSpeak(current.word) },
                                haptic = haptic,
                                modifier = Modifier.fillMaxWidth().height(440.dp)
                            )
                        }
                        // 自动播放发音
                        if (state.autoPlayTts) {
                            LaunchedEffect(current.id) {
                                delay(350)
                                onSpeak(current.word)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                GestureHints()
                Spacer(Modifier.height(20.dp))
            }
        }

        // 本轮统计弹窗
        if (state.finished) {
            SessionResultDialog(
                state = state,
                onExit = onExit,
                onRestart = onRestart
            )
        }

        // 成就庆祝弹窗
        if (state.newAchievements.isNotEmpty()) {
            AchievementDialog(
                achievements = state.newAchievements,
                onDismiss = onDismissAchievements
            )
        }
    }
}

@Composable
fun SessionResultDialog(
    state: StudyUiState,
    onExit: () -> Unit,
    onRestart: () -> Unit
) {
    val seconds = ((System.currentTimeMillis() - state.startTime) / 1000).toInt()
    val timeText = "%d分%02d秒".format(seconds / 60, seconds % 60)

    AlertDialog(
        onDismissRequest = {},
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎉", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text("本组学习完成！", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "${state.knownCount}", label = "认识")
                StatItem(
                    value = "${state.unknownCount}",
                    label = "不认识",
                    valueColor = MaterialTheme.colorScheme.error
                )
                StatItem(value = "${state.favoriteCount}", label = "收藏")
                StatItem(value = timeText, label = "用时")
            }
        },
        confirmButton = {
            Button(onClick = onRestart) { Text("再来一组") }
        },
        dismissButton = {
            TextButton(onClick = onExit) { Text("返回") }
        }
    )
}

@Composable
fun AchievementDialog(
    achievements: List<AchievementDef>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                var scale by remember { mutableFloatStateOf(0f) }
                val animatedScale by animateFloatAsState(
                    targetValue = scale,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f),
                    label = "trophy"
                )
                LaunchedEffect(Unit) { scale = 1f }
                Text("🏆", fontSize = 52.sp, modifier = Modifier.scale(animatedScale))
                Spacer(Modifier.height(8.dp))
                Text("解锁新成就！", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                achievements.forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(a.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.title, fontWeight = FontWeight.Bold)
                            Text(
                                a.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("太棒了") }
        }
    )
}
