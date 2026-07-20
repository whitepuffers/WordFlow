package com.wordflow.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflow.app.data.model.QuizMode
import com.wordflow.app.data.model.StudySource
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import com.wordflow.app.domain.AchievementDef
import com.wordflow.app.domain.QuizGenerator
import com.wordflow.app.domain.QuizQuestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizPhase { MODE_SELECT, IN_PROGRESS, RESULT }

data class WrongItem(
    val word: String,
    val meaning: String,
    val yourAnswer: String
)

data class QuizUiState(
    val phase: QuizPhase = QuizPhase.MODE_SELECT,
    val loading: Boolean = false,
    val error: String? = null,
    val mode: QuizMode = QuizMode.CHOICE,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val answered: Boolean = false,
    val selectedIndex: Int = -1,
    val lastCorrect: Boolean = false,
    val fillInput: String = "",
    val correctCount: Int = 0,
    val wrongItems: List<WrongItem> = emptyList(),
    val timeLeft: Int = 15,
    val perQuestionSeconds: Int = 15,
    val elapsedSeconds: Int = 0,
    val newAchievements: List<AchievementDef> = emptyList()
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val total: Int get() = questions.size
}

class QuizViewModel(
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        const val QUESTION_COUNT = 10
        const val FEEDBACK_DELAY_MS = 900L
    }

    private val _ui = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _ui.asStateFlow()

    private var timerJob: Job? = null
    private var quizStartTime = 0L

    fun start(mode: QuizMode) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, mode = mode) }
            val settings = settingsRepository.settings.first()
            val book = settings.currentBook

            // 优先考已学过的词，不足则用全库随机词补齐
            val seen = wordRepository.randomSeenWords(book, 60)
            val pool = if (seen.size >= QUESTION_COUNT) {
                seen
            } else {
                (seen + wordRepository.randomWords(book, 60)).distinctBy { it.id }
            }

            val questions = QuizGenerator.generate(mode, pool, QUESTION_COUNT)
            if (questions.isEmpty()) {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = "当前词库单词太少，无法生成题目。\n请先在设置中切换词库。"
                    )
                }
                return@launch
            }

            quizStartTime = System.currentTimeMillis()
            _ui.update {
                it.copy(
                    loading = false,
                    phase = QuizPhase.IN_PROGRESS,
                    questions = questions,
                    index = 0,
                    answered = false,
                    selectedIndex = -1,
                    correctCount = 0,
                    wrongItems = emptyList(),
                    fillInput = "",
                    perQuestionSeconds = settings.quizSeconds,
                    newAchievements = emptyList()
                )
            }
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _ui.update { it.copy(timeLeft = it.perQuestionSeconds) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _ui.value
                if (s.answered) break
                if (s.timeLeft <= 1) {
                    submitAnswerInternal(selected = -1, textAnswer = null)
                    break
                }
                _ui.update { it.copy(timeLeft = it.timeLeft - 1) }
            }
        }
    }

    /** 选择题 / 听力题作答 */
    fun answerChoice(selected: Int) {
        if (_ui.value.answered) return
        submitAnswerInternal(selected = selected, textAnswer = null)
    }

    /** 填空题作答 */
    fun answerFillBlank(input: String) {
        if (_ui.value.answered) return
        submitAnswerInternal(selected = -1, textAnswer = input)
    }

    fun updateFillInput(value: String) {
        if (!_ui.value.answered) {
            _ui.update { it.copy(fillInput = value) }
        }
    }

    private fun submitAnswerInternal(selected: Int, textAnswer: String?) {
        val s = _ui.value
        val question = s.current ?: return
        if (s.answered) return

        timerJob?.cancel()

        val correct = if (textAnswer != null) {
            normalize(textAnswer) == normalize(question.answer)
        } else {
            selected == question.correctIndex
        }

        _ui.update {
            it.copy(
                answered = true,
                selectedIndex = selected,
                lastCorrect = correct,
                correctCount = it.correctCount + if (correct) 1 else 0,
                wrongItems = if (correct) it.wrongItems
                else it.wrongItems + WrongItem(
                    word = question.word,
                    meaning = question.meaning,
                    yourAnswer = textAnswer?.takeIf { t -> t.isNotBlank() }
                        ?: question.options.getOrNull(selected)
                        ?: "（超时未答）"
                )
            )
        }

        viewModelScope.launch {
            // 答题结果也进入 SM-2 复习计划
            val achievements = studyRepository.applyResult(question.wordId, correct, StudySource.QUIZ)
            _ui.update { it.copy(newAchievements = it.newAchievements + achievements) }

            delay(FEEDBACK_DELAY_MS)
            advance()
        }
    }

    private suspend fun advance() {
        val s = _ui.value
        if (s.index + 1 >= s.questions.size) {
            // 完成
            val elapsed = ((System.currentTimeMillis() - quizStartTime) / 1000).toInt()
            val perfect = s.wrongItems.isEmpty()
            val achievements = studyRepository.checkAchievements(
                quizFinished = true,
                quizPerfect = perfect
            )
            _ui.update {
                it.copy(
                    phase = QuizPhase.RESULT,
                    elapsedSeconds = elapsed,
                    newAchievements = it.newAchievements + achievements
                )
            }
        } else {
            _ui.update {
                it.copy(
                    index = it.index + 1,
                    answered = false,
                    selectedIndex = -1,
                    fillInput = ""
                )
            }
            startTimer()
        }
    }

    fun backToModeSelect() {
        timerJob?.cancel()
        _ui.value = QuizUiState()
    }

    fun dismissAchievements() {
        _ui.update { it.copy(newAchievements = emptyList()) }
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
