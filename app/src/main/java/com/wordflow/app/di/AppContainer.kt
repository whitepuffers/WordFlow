package com.wordflow.app.di

import android.content.Context
import androidx.room.Room
import com.wordflow.app.data.db.AppDatabase
import com.wordflow.app.data.prefs.SettingsRepository
import com.wordflow.app.data.repo.StudyRepository
import com.wordflow.app.data.repo.WordRepository
import com.wordflow.app.tts.TtsManager

/**
 * 手写依赖注入容器，避免引入 Hilt 带来的额外复杂度。
 * 通过 [com.wordflow.app.ui.LocalAppContainer] 暴露给 Compose 层。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "wordflow.db"
    )
        .fallbackToDestructiveMigration()
        .build()

    val settingsRepository = SettingsRepository(appContext)
    val wordRepository = WordRepository(appContext, database)
    val studyRepository = StudyRepository(database)
    val ttsManager = TtsManager(appContext)
}
