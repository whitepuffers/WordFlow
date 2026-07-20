package com.wordflow.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordflow.app.data.model.ThemeMode
import com.wordflow.app.data.prefs.AppSettings
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.components.SectionCard
import com.wordflow.app.ui.rememberAppHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsRoute() {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                container.appContext,
                container.settingsRepository,
                container.wordRepository,
                container.studyRepository
            )
        }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = state.settings
    val haptic = rememberAppHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimePicker by remember { mutableStateOf(false) }
    var showBookDialog by remember { mutableStateOf(false) }
    var pendingBook by remember { mutableStateOf<Pair<String, String>?>(null) } // code to name
    var showResetDialog by remember { mutableStateOf(false) }

    // Android 13+ 通知权限
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            scope.launch { snackbarHostState.showSnackbar("未授予通知权限，无法开启提醒") }
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // ---- 学习目标 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("每日学习目标")
                    Text(
                        "每天学习 ${settings.dailyGoal} 个新单词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = settings.dailyGoal.toFloat(),
                        onValueChange = {
                            haptic(HapticFeedbackType.SegmentTick)
                            viewModel.setDailyGoal((it / 5).roundToInt() * 5)
                        },
                        valueRange = 10f..100f,
                        steps = 17
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 学习提醒 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("学习提醒")
                    SettingSwitchRow(
                        title = "每日提醒",
                        checked = settings.reminderEnabled,
                        onCheckedChange = { checked ->
                            haptic(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            if (checked) {
                                val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setReminderEnabled(true)
                                }
                            } else {
                                viewModel.setReminderEnabled(false)
                            }
                        }
                    )
                    if (settings.reminderEnabled) {
                        Surface(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                showTimePicker = true
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("提醒时间", modifier = Modifier.weight(1f))
                                Text(
                                    settings.reminderTime,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 测试设置 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("测试设置")
                    Text(
                        "每题限时 ${settings.quizSeconds} 秒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = settings.quizSeconds.toFloat(),
                        onValueChange = {
                            haptic(HapticFeedbackType.SegmentTick)
                            viewModel.setQuizSeconds(it.roundToInt())
                        },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 反馈 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("反馈")
                    SettingSwitchRow("音效", settings.soundEnabled) {
                        haptic(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        viewModel.setSoundEnabled(it)
                    }
                    SettingSwitchRow("震动反馈", settings.hapticsEnabled) {
                        viewModel.setHapticsEnabled(it)
                    }
                    SettingSwitchRow("自动播放发音", settings.autoPlayTts) {
                        haptic(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        viewModel.setAutoPlayTts(it)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 外观 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("外观")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    viewModel.setThemeMode(mode)
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 考试词库 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("考试词库")
                    val currentBook = state.books.firstOrNull { it.code == settings.currentBook }
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            showBookDialog = true
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("当前词库", modifier = Modifier.weight(1f))
                            Text(
                                "${currentBook?.nameZh ?: settings.currentBook}（${currentBook?.wordCount ?: 0} 词）",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 危险区 ----
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSectionTitle("危险区")
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            showResetDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重置学习进度")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "WordFlow v1.0 · SM-2 间隔重复算法",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- 时间选择弹窗 ----
    if (showTimePicker) {
        val (initH, initM) = remember(settings.reminderTime) {
            val parts = settings.reminderTime.split(":")
            (parts.getOrNull(0)?.toIntOrNull() ?: 20) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        val timeState = rememberTimePickerState(initialHour = initH, initialMinute = initM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderTime("%02d:%02d".format(timeState.hour, timeState.minute))
                    haptic(HapticFeedbackType.TextHandleMove)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    // ---- 词库选择弹窗 ----
    if (showBookDialog) {
        AlertDialog(
            onDismissRequest = { showBookDialog = false },
            title = { Text("切换考试词库") },
            text = {
                Column {
                    state.books.forEach { book ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = book.code == settings.currentBook,
                                onClick = {
                                    if (book.code != settings.currentBook) {
                                        pendingBook = book.code to book.nameZh
                                        showBookDialog = false
                                    }
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text("${book.nameZh}（${book.nameEn}）", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${book.wordCount} 词",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookDialog = false }) { Text("关闭") }
            }
        )
    }

    // ---- 词库切换二次确认 ----
    pendingBook?.let { (code, name) ->
        AlertDialog(
            onDismissRequest = { pendingBook = null },
            title = { Text("切换到「$name」？") },
            text = { Text("切换后首页、学习与测试将使用新词库。各词库的学习进度相互独立，不会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.switchBook(code)
                    haptic(HapticFeedbackType.LongPress)
                    pendingBook = null
                    scope.launch { snackbarHostState.showSnackbar("已切换到「$name」") }
                }) { Text("确认切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBook = null }) { Text("取消") }
            }
        )
    }

    // ---- 重置二次确认 ----
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置学习进度？") },
            text = { Text("将清空全部学习记录、复习进度与成就（词库数据保留）。此操作不可恢复！") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetProgress {
                            scope.launch { snackbarHostState.showSnackbar("学习进度已重置") }
                        }
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
