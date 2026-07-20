package com.wordflow.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wordflow.app.data.db.dao.AchievementDao
import com.wordflow.app.data.db.dao.ProgressDao
import com.wordflow.app.data.db.dao.StudyLogDao
import com.wordflow.app.data.db.dao.WordDao
import com.wordflow.app.data.db.entity.AchievementEntity
import com.wordflow.app.data.db.entity.StudyLogEntity
import com.wordflow.app.data.db.entity.WordBookEntity
import com.wordflow.app.data.db.entity.WordEntity
import com.wordflow.app.data.db.entity.WordProgressEntity

@Database(
    entities = [
        WordBookEntity::class,
        WordEntity::class,
        WordProgressEntity::class,
        StudyLogEntity::class,
        AchievementEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun studyLogDao(): StudyLogDao
    abstract fun achievementDao(): AchievementDao
}
