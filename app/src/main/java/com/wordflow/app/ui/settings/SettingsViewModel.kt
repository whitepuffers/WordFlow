package com.wordflow.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflow.app.data.db.entity.WordBookEntity
import com.wordflow.app.data.model.ThemeMode
import com.wordflow.app.data.prefs.AppSettings
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import com.wordflow.app.work.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val books: List<WordBookEntity> = emptyList(),
    val resetDone: Boolean = false
)

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        wordRepository.observeBooks()
    ) { settings, books ->
        SettingsUiState(settings = settings, books = books)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setDailyGoal(value: Int) = launch { settingsRepository.setDailyGoal(value) }

    fun setReminderEnabled(enabled: Boolean) = launch {
        settingsRepository.setReminderEnabled(enabled)
        val time = settingsRepository.settings.first().reminderTime
        if (enabled) ReminderScheduler.schedule(appContext, time)
        else ReminderScheduler.cancel(appContext)
    }

    fun setReminderTime(time: String) = launch {
        settingsRepository.setReminderTime(time)
        if (settingsRepository.settings.first().reminderEnabled) {
            ReminderScheduler.schedule(appContext, time)
        }
    }

    fun setSoundEnabled(enabled: Boolean) = launch { settingsRepository.setSoundEnabled(enabled) }
    fun setHapticsEnabled(enabled: Boolean) = launch { settingsRepository.setHapticsEnabled(enabled) }
    fun setAutoPlayTts(enabled: Boolean) = launch { settingsRepository.setAutoPlayTts(enabled) }
    fun setThemeMode(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setQuizSeconds(seconds: Int) = launch { settingsRepository.setQuizSeconds(seconds) }

    fun switchBook(code: String) = launch {
        settingsRepository.setCurrentBook(code)
    }

    fun resetProgress(onDone: () -> Unit) = launch {
        studyRepository.resetAll()
        onDone()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
