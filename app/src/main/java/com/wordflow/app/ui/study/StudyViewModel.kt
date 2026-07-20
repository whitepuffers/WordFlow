package com.wordflow.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflow.app.data.db.entity.WordEntity
import com.wordflow.app.data.model.StudySource
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import com.wordflow.app.domain.AchievementDef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DeckMode(val raw: String) {
    NEW("new"),
    REVIEW("review");

    companion object {
        fun from(raw: String?): DeckMode = entries.firstOrNull { it.raw == raw } ?: NEW
    }
}

data class StudyUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val emptyEmoji: String = "📭",
    val emptyTitle: String = "",
    val emptyMessage: String = "",
    val words: List<WordEntity> = emptyList(),
    val index: Int = 0,
    val finished: Boolean = false,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val favoriteIds: Set<Long> = emptySet(),
    val newAchievements: List<AchievementDef> = emptyList(),
    val autoPlayTts: Boolean = false,
    val startTime: Long = System.currentTimeMillis()
) {
    val current: WordEntity? get() = words.getOrNull(index)
    val next: WordEntity? get() = words.getOrNull(index + 1)
    val total: Int get() = words.size
    val favoriteCount: Int get() = favoriteIds.size
}

class StudyViewModel(
    private val mode: DeckMode,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        const val NEW_GROUP_SIZE = 20
        const val REVIEW_GROUP_SIZE = 50
    }

    private val _ui = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, empty = false, finished = false) }
            val settings = settingsRepository.settings.first()
            val book = settings.currentBook

            when (mode) {
                DeckMode.NEW -> {
                    val remaining = settings.dailyGoal - studyRepository.todayCount()
                    if (remaining <= 0) {
                        _ui.update {
                            it.copy(
                                loading = false, empty = true,
                                emptyEmoji = "🎉",
                                emptyTitle = "今日目标已完成！",
                                emptyMessage = "可以去复习巩固，或来一组随机测试"
                            )
                        }
                        return@launch
                    }
                    val words = wordRepository.newWords(book, minOf(remaining, NEW_GROUP_SIZE))
                    if (words.isEmpty()) {
                        _ui.update {
                            it.copy(
                                loading = false, empty = true,
                                emptyEmoji = "🎓",
                                emptyTitle = "本词库已全部学完",
                                emptyMessage = "太厉害了！可以在设置里切换到其他词库"
                            )
                        }
                    } else {
                        _ui.update {
                            StudyUiState(
                                loading = false,
                                words = words,
                                autoPlayTts = settings.autoPlayTts
                            )
                        }
                    }
                }

                DeckMode.REVIEW -> {
                    val words = wordRepository.dueWords(
                        book,
                        LocalDate.now().toEpochDay(),
                        REVIEW_GROUP_SIZE
                    )
                    if (words.isEmpty()) {
                        _ui.update {
                            it.copy(
                                loading = false, empty = true,
                                emptyEmoji = "✨",
                                emptyTitle = "没有待复习的单词",
                                emptyMessage = "所有单词都在按计划休息，晚点再来吧"
                            )
                        }
                    } else {
                        _ui.update {
                            StudyUiState(
                                loading = false,
                                words = words,
                                autoPlayTts = settings.autoPlayTts
                            )
                        }
                    }
                }
            }
        }
    }

    /** 标记当前单词：认识 / 不认识 */
    fun mark(known: Boolean) {
        val state = _ui.value
        val word = state.current ?: return
        val source = if (mode == DeckMode.REVIEW) StudySource.REVIEW else StudySource.STUDY

        viewModelScope.launch {
            val achievements = studyRepository.applyResult(word.id, known, source)
            _ui.update { s ->
                val nextIndex = s.index + 1
                s.copy(
                    index = nextIndex,
                    finished = nextIndex >= s.words.size,
                    knownCount = s.knownCount + if (known) 1 else 0,
                    unknownCount = s.unknownCount + if (known) 0 else 1,
                    newAchievements = s.newAchievements + achievements
                )
            }
        }
    }

    /** 上滑收藏 / 点击爱心取消收藏 */
    fun toggleFavorite() {
        val word = _ui.value.current ?: return
        viewModelScope.launch {
            val achievements = studyRepository.toggleFavorite(word.id)
            _ui.update { s ->
                val ids = s.favoriteIds
                val newIds = if (word.id in ids) ids - word.id else ids + word.id
                s.copy(
                    favoriteIds = newIds,
                    newAchievements = s.newAchievements + achievements
                )
            }
        }
    }

    fun dismissAchievements() {
        _ui.update { it.copy(newAchievements = emptyList()) }
    }

    /** 再来一组 */
    fun restart() {
        _ui.value = StudyUiState()
        load()
    }
}
