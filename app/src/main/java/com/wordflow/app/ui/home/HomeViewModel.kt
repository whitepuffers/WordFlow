package com.wordflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val bookName: String = "",
    val bookNameEn: String = "",
    val bookWordCount: Int = 0,
    val dailyGoal: Int = 20,
    val todayLearned: Int = 0,
    val streak: Int = 0,
    val dueCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    studyRepository: StudyRepository
) : ViewModel() {

    private val today = LocalDate.now().toEpochDay()

    private val dueCountFlow = settingsRepository.settings.flatMapLatest { settings ->
        wordRepository.observeDueCount(settings.currentBook, today)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        studyRepository.observeTodayCount(),
        studyRepository.observeStreak(),
        wordRepository.observeBooks(),
        dueCountFlow
    ) { settings, todayCount, streak, books, dueCount ->
        val book = books.firstOrNull { it.code == settings.currentBook }
        HomeUiState(
            loading = books.isEmpty(),
            bookName = book?.nameZh ?: "",
            bookNameEn = book?.nameEn ?: "",
            bookWordCount = book?.wordCount ?: 0,
            dailyGoal = settings.dailyGoal,
            todayLearned = todayCount,
            streak = streak,
            dueCount = dueCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
