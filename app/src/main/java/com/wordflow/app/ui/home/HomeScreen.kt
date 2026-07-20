package com.wordflow.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.components.LoadingState
import com.wordflow.app.ui.components.ProgressRing
import com.wordflow.app.ui.components.SectionCard
import com.wordflow.app.ui.components.StatItem
import com.wordflow.app.ui.rememberAppHaptics
import com.wordflow.app.ui.theme.Orange500

@Composable
fun HomeRoute(
    onStartStudy: () -> Unit,
    onStartReview: () -> Unit,
    onStartQuiz: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer {
            HomeViewModel(
                container.settingsRepository,
                container.wordRepository,
                container.studyRepository
            )
        }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        LoadingState(message = "正在初始化词库…")
    } else {
        HomeScreen(
            state = state,
            onStartStudy = onStartStudy,
            onStartReview = onStartReview,
            onStartQuiz = onStartQuiz,
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartStudy: () -> Unit,
    onStartReview: () -> Unit,
    onStartQuiz: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val haptic = rememberAppHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ---- 顶部栏：问候 + 词库 + 设置 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WordFlow",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onOpenSettings()
                    }
                ) {
                    Text(
                        "${state.bookName} · ${state.bookWordCount} 词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "切换词库",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onOpenSettings()
            }) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- 进度卡片：环形进度 + 连续打卡 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRing(
                    progress = if (state.dailyGoal > 0) state.todayLearned.toFloat() / state.dailyGoal else 0f,
                    modifier = Modifier.size(132.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.todayLearned}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "/ ${state.dailyGoal} 词",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "今日已学",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(24.dp))

                Column(Modifier.weight(1f)) {
                    // 连续打卡
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Orange500.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Orange500,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "连续打卡 ${state.streak} 天",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Orange500
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 待复习角标
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onStartReview()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "待复习",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("${state.dueCount}")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "快捷操作",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        // ---- 快捷按钮 ----
        Button(
            onClick = {
                haptic(HapticFeedbackType.LongPress)
                onStartStudy()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("开始学习", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        onStartReview()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("快速复习")
                }
                if (state.dueCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 8.dp)
                    ) { Text("${state.dueCount}") }
                }
            }

            FilledTonalButton(
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onStartQuiz()
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("随机测试")
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 今日小结 ----
        SectionCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "${state.todayLearned}", label = "今日已学")
                StatItem(
                    value = "${(state.dailyGoal - state.todayLearned).coerceAtLeast(0)}",
                    label = "剩余目标",
                    valueColor = MaterialTheme.colorScheme.secondary
                )
                StatItem(value = "${state.streak}", label = "连续天数", valueColor = Orange500)
                StatItem(
                    value = "${state.dueCount}",
                    label = "待复习",
                    valueColor = if (state.dueCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
