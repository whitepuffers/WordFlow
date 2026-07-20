package com.wordflow.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordflow.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val dailyGoal: Int = 20,
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val autoPlayTts: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currentBook: String = "cet4",
    /** 每题倒计时秒数 */
    val quizSeconds: Int = 15
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val AUTO_TTS = booleanPreferencesKey("auto_tts")
        val THEME = stringPreferencesKey("theme_mode")
        val BOOK = stringPreferencesKey("current_book")
        val QUIZ_SECONDS = intPreferencesKey("quiz_seconds")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            dailyGoal = p[Keys.DAILY_GOAL] ?: 20,
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: false,
            reminderTime = p[Keys.REMINDER_TIME] ?: "20:00",
            soundEnabled = p[Keys.SOUND] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            autoPlayTts = p[Keys.AUTO_TTS] ?: false,
            themeMode = ThemeMode.from(p[Keys.THEME]),
            currentBook = p[Keys.BOOK] ?: "cet4",
            quizSeconds = p[Keys.QUIZ_SECONDS] ?: 15
        )
    }

    suspend fun setDailyGoal(value: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL] = value.coerceIn(10, 100) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(time: String) {
        context.dataStore.edit { it[Keys.REMINDER_TIME] = time }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setAutoPlayTts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_TTS] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.raw }
    }

    suspend fun setCurrentBook(code: String) {
        context.dataStore.edit { it[Keys.BOOK] = code }
    }

    suspend fun setQuizSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.QUIZ_SECONDS] = seconds.coerceIn(5, 60) }
    }
}
