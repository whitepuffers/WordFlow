package com.wordflow.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflow.app.data.db.dao.DayCount
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import com.wordflow.app.domain.AchievementDef
import com.wordflow.app.domain.Achievements
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class AchievementUi(
    val def: AchievementDef,
    val unlocked: Boolean
)

data class StatsUiState(
    val loading: Boolean = true,
    val hasData: Boolean = false,
    /** 最近 126 天每天学习数：key=epochDay */
    val dailyCounts: Map<Long, Int> = emptyMap(),
    val todayEpochDay: Long = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val masteredCount: Int = 0,
    val totalLearned: Int = 0,
    val streak: Int = 0,
    val achievements: List<AchievementUi> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    settingsRepository: SettingsRepository,
    wordRepository: WordRepository,
    studyRepository: StudyRepository
) : ViewModel() {

    private val today = LocalDate.now().toEpochDay()

    private val masteryFlow = settingsRepository.settings.flatMapLatest {
        wordRepository.observeMastery(it.currentBook)
    }
    private val wordCountFlow = settingsRepository.settings.flatMapLatest {
        wordRepository.observeWordCount(it.currentBook)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        studyRepository.observeDailyCounts(126),
        masteryFlow,
        wordCountFlow,
        studyRepository.observeStreak(),
        studyRepository.observeAchievements()
    ) { daily, mastery, wordCount, streak, achievementEntities ->
        val learning = mastery.firstOrNull { it.status == 1 }?.count ?: 0
        val mastered = mastery.firstOrNull { it.status == 2 }?.count ?: 0
        val learned = learning + mastered
        StatsUiState(
            loading = false,
            hasData = daily.isNotEmpty(),
            dailyCounts = daily.associate { it.day to it.count },
            todayEpochDay = today,
            newCount = (wordCount - learned).coerceAtLeast(0),
            learningCount = learning,
            masteredCount = mastered,
            totalLearned = daily.sumOf { it.count },
            streak = streak,
            achievements = Achievements.ALL.map { def ->
                AchievementUi(
                    def = def,
                    unlocked = achievementEntities.firstOrNull { it.id == def.id }?.unlockedAt != null
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )
}
