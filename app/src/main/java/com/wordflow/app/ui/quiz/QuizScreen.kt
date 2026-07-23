package com.wordflow.app.ui.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordflow.app.data.model.QuizMode
import com.wordflow.app.domain.QuizQuestion
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.LocalSoundEnabled
import com.wordflow.app.ui.components.ErrorState
import com.wordflow.app.ui.components.LoadingState
import com.wordflow.app.ui.components.SectionCard
import com.wordflow.app.ui.components.StatItem
import com.wordflow.app.ui.rememberAppHaptics
import com.wordflow.app.tts.SoundPlayer
import com.wordflow.app.ui.study.AchievementDialog
import com.wordflow.app.ui.theme.Error500
import com.wordflow.app.ui.theme.Success500
import kotlinx.coroutines.delay

@Composable
fun QuizRoute() {
    val container = LocalAppContainer.current
    val viewModel: QuizViewModel = viewModel(factory = viewModelFactory {
        initializer {
            QuizViewModel(
                container.wordRepository,
                container.studyRepository,
                container.settingsRepository
            )
        }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberAppHaptics()
    val soundEnabled = LocalSoundEnabled.current

    // 答题反馈：音效 + 震动
    LaunchedEffect(state.answered, state.index) {
        if (state.phase == QuizPhase.IN_PROGRESS && state.answered) {
            if (state.lastCorrect) {
                if (soundEnabled) SoundPlayer.correct()
                haptic(HapticFeedbackType.TextHandleMove)
            } else {
                if (soundEnabled) SoundPlayer.wrong()
                haptic(HapticFeedbackType.LongPress)
            }
        }
    }
    // 成就庆祝
    LaunchedEffect(state.newAchievements) {
        if (state.newAchievements.isNotEmpty()) {
            if (soundEnabled) SoundPlayer.celebrate()
            haptic(HapticFeedbackType.LongPress)
        }
    }
    // 听力题：进入新题自动播放
    LaunchedEffect(state.index, state.phase) {
        if (state.phase == QuizPhase.IN_PROGRESS && state.mode == QuizMode.LISTENING && soundEnabled) {
            delay(300)
            state.current?.let { container.ttsManager.speak(it.word) }
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        when (state.phase) {
            QuizPhase.MODE_SELECT -> QuizModeSelectScreen(
                loading = state.loading,
                error = state.error,
                onSelect = viewModel::start
            )

            QuizPhase.IN_PROGRESS -> QuizInProgressScreen(
                state = state,
                onChoice = viewModel::answerChoice,
                onFillChange = viewModel::updateFillInput,
                onFillSubmit = { viewModel.answerFillBlank(state.fillInput) },
                onSpeak = { if (soundEnabled) container.ttsManager.speak(state.current?.word ?: "") }
            )

            QuizPhase.RESULT -> QuizResultScreen(
                state = state,
                onRestart = viewModel::backToModeSelect
            )
        }

        if (state.newAchievements.isNotEmpty()) {
            AchievementDialog(
                achievements = state.newAchievements,
                onDismiss = viewModel::dismissAchievements
            )
        }
    }
}

// ---------- 题型选择 ----------

@Composable
fun QuizModeSelectScreen(
    loading: Boolean,
    error: String?,
    onSelect: (QuizMode) -> Unit
) {
    if (loading) {
        LoadingState(message = "正在出题…")
        return
    }
    if (error != null) {
        ErrorState(message = error)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        Text(
            "练习测试",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "共 10 题，选择一种题型开始",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        QuizModeCard(
            icon = Icons.Default.Quiz,
            title = QuizMode.CHOICE.title,
            desc = QuizMode.CHOICE.desc,
            onClick = { onSelect(QuizMode.CHOICE) }
        )
        Spacer(Modifier.height(16.dp))
        QuizModeCard(
            icon = Icons.Default.Edit,
            title = QuizMode.FILL_BLANK.title,
            desc = QuizMode.FILL_BLANK.desc,
            onClick = { onSelect(QuizMode.FILL_BLANK) }
        )
        Spacer(Modifier.height(16.dp))
        QuizModeCard(
            icon = Icons.Default.Headphones,
            title = QuizMode.LISTENING.title,
            desc = QuizMode.LISTENING.desc,
            onClick = { onSelect(QuizMode.LISTENING) }
        )
    }
}

@Composable
private fun QuizModeCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------- 答题中 ----------

@Composable
fun QuizInProgressScreen(
    state: QuizUiState,
    onChoice: (Int) -> Unit,
    onFillChange: (String) -> Unit,
    onFillSubmit: () -> Unit,
    onSpeak: () -> Unit
) {
    val question = state.current ?: return

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        // 顶部：题号 + 倒计时进度条
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.index + 1}/${state.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            val timeRatio by animateFloatAsState(
                targetValue = state.timeLeft.toFloat() / state.perQuestionSeconds,
                animationSpec = tween(300),
                label = "timer"
            )
            LinearProgressIndicator(
                progress = { timeRatio },
                modifier = Modifier.weight(1f),
                color = if (state.timeLeft <= 5) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${state.timeLeft}s",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (state.timeLeft <= 5) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(28.dp))

        // 题干
        when (state.mode) {
            QuizMode.CHOICE -> {
                Text(
                    question.word,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (question.phonetic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        question.phonetic,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            QuizMode.FILL_BLANK -> {
                Text(
                    question.meaning,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "写出对应的英文单词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            QuizMode.LISTENING -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onSpeak
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "播放发音",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "播放发音",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // 作答区
        when (state.mode) {
            QuizMode.FILL_BLANK -> FillBlankAnswer(
                state = state,
                question = question,
                onChange = onFillChange,
                onSubmit = onFillSubmit
            )

            else -> ChoiceOptions(
                state = state,
                question = question,
                onChoice = onChoice
            )
        }
    }
}

@Composable
private fun ChoiceOptions(
    state: QuizUiState,
    question: QuizQuestion,
    onChoice: (Int) -> Unit
) {
    val shake = remember { Animatable(0f) }

    // 答错时抖动
    LaunchedEffect(state.answered, state.index) {
        if (state.answered && !state.lastCorrect) {
            repeat(3) {
                shake.animateTo(14f, tween(60))
                shake.animateTo(-14f, tween(60))
            }
            shake.animateTo(0f, tween(60))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.forEachIndexed { index, option ->
            val isCorrect = index == question.correctIndex
            val isSelected = index == state.selectedIndex

            val containerColor = when {
                state.answered && isCorrect -> Success500.copy(alpha = 0.18f)
                state.answered && isSelected -> Error500.copy(alpha = 0.18f)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                state.answered && isCorrect -> Success500
                state.answered && isSelected -> Error500
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            val shakeOffset = if (state.answered && isSelected && !state.lastCorrect) shake.value else 0f

            Card(
                onClick = { if (!state.answered) onChoice(index) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeOffset }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${'A' + index}",
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.answered && isCorrect) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "正确答案",
                            tint = Success500
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FillBlankAnswer(
    state: QuizUiState,
    question: QuizQuestion,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = state.fillInput,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.answered,
            label = { Text("输入英文单词") },
            singleLine = true,
            isError = state.answered && !state.lastCorrect
        )
        Spacer(Modifier.height(16.dp))

        if (!state.answered) {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = state.fillInput.isNotBlank()
            ) {
                Text("提交答案", fontWeight = FontWeight.Bold)
            }
        } else {
            // 反馈
            val correct = state.lastCorrect
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (correct) Success500.copy(alpha = 0.15f) else Error500.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (correct) "✓ 回答正确" else "✗ 回答错误",
                        fontWeight = FontWeight.Bold,
                        color = if (correct) Success500 else Error500
                    )
                    if (!correct) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正确答案：${question.answer}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ---------- 结果页 ----------

@Composable
fun QuizResultScreen(
    state: QuizUiState,
    onRestart: () -> Unit
) {
    val accuracy = if (state.total == 0) 0 else state.correctCount * 100 / state.total
    val timeText = "%d分%02d秒".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        var scale by remember { mutableFloatStateOf(0f) }
        val animatedScale by animateFloatAsState(
            targetValue = scale,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 250f),
            label = "resultEmoji"
        )
        LaunchedEffect(Unit) { scale = 1f }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (accuracy == 100) "🏆" else if (accuracy >= 60) "🎉" else "💪",
                fontSize = 56.sp,
                modifier = Modifier.scale(animatedScale)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (accuracy == 100) "满分！太厉害了！"
                else if (accuracy >= 60) "测试完成！"
                else "继续加油！",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "$accuracy%",
                    label = "正确率",
                    valueColor = if (accuracy >= 60) Success500 else MaterialTheme.colorScheme.error
                )
                StatItem(value = "${state.correctCount}/${state.total}", label = "答对题数")
                StatItem(value = timeText, label = "用时")
            }
        }

        if (state.wrongItems.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "错题回顾（${state.wrongItems.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            state.wrongItems.forEach { item ->
                SectionCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            item.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "你的答案：${item.yourAnswer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("再来一组", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}
