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
import com.wordflow.app.tts.TtsEngineInfo
import com.wordflow.app.tts.TtsManager
import com.wordflow.app.tts.TtsStatus
import com.wordflow.app.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val books: List<WordBookEntity> = emptyList(),
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
    val ttsErrorCode: Int? = null,
    val ttsEngine: String? = null,
    val availableEngines: List<TtsEngineInfo> = emptyList(),
    val musicVolume: Int = -1,
    val showTtsErrorDialog: Boolean = false,
    val resetDone: Boolean = false
)

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _showTtsErrorDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        wordRepository.observeBooks(),
        combine(
            ttsManager.status,
            ttsManager.errorCode,
            ttsManager.currentEngine,
            ttsManager.availableEngines,
            _showTtsErrorDialog
        ) { status, error, engine, available, showDialog ->
            TtsInfo(status, error, engine, available, showDialog)
        }
    ) { settings, books, ttsInfo ->
        SettingsUiState(
            settings = settings,
            books = books,
            ttsStatus = ttsInfo.status,
            ttsErrorCode = ttsInfo.error,
            ttsEngine = ttsInfo.engine,
            availableEngines = ttsInfo.available,
            musicVolume = ttsManager.getMusicVolume(),
            showTtsErrorDialog = ttsInfo.showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private data class TtsInfo(
        val status: TtsStatus,
        val error: Int?,
        val engine: String?,
        val available: List<TtsEngineInfo>,
        val showDialog: Boolean
    )

    init {
        // 首次初始化引擎：尝试使用用户保存的引擎
        launch {
            val savedEngine = settingsRepository.settings.first().ttsEngine
            ttsManager.initTts(savedEngine)
        }

        // 当 TTS 状态变为错误时，且自动播放开启，自动提示
        launch {
            combine(ttsManager.status, settingsRepository.settings) { status, settings ->
                status to settings.autoPlayTts
            }.collect { (status, autoPlay) ->
                if (autoPlay && (status == TtsStatus.LANGUAGE_NOT_SUPPORTED || status == TtsStatus.INITIALIZATION_FAILED)) {
                    _showTtsErrorDialog.value = true
                }
            }
        }
    }

    fun dismissTtsErrorDialog() {
        _showTtsErrorDialog.value = false
    }

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

    fun setTtsEngine(engine: String?) = launch {
        settingsRepository.setTtsEngine(engine)
        ttsManager.initTts(engine)
    }

    fun playTestBeep(streamType: Int) {
        ttsManager.playTestBeep(streamType)
    }

    fun installTts() {
        ttsManager.installTts(appContext)
    }

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
