package com.wordflow.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 词库（四级/六级/托福/雅思） */
@Entity(tableName = "word_books")
data class WordBookEntity(
    @PrimaryKey val code: String,
    val nameZh: String,
    val nameEn: String,
    val wordCount: Int,
    /** 词库数据版本号：assets 数据更新后 +1，触发重新导入 */
    val assetVersion: Int
)

/** 单词 */
@Entity(
    tableName = "words",
    indices = [Index(value = ["bookCode", "word"], unique = true), Index("bookCode")]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookCode: String,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val example: String,
    val exampleCn: String
)

/** 每个单词的学习进度（SM-2 状态） */
@Entity(tableName = "word_progress")
data class WordProgressEntity(
    @PrimaryKey val wordId: Long,
    /** 0=新学 1=学习中 2=已掌握 */
    val status: Int = 0,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    /** 下次复习日期（LocalDate.toEpochDay） */
    val dueDay: Long = 0,
    val lastReviewDay: Long = 0,
    val favorite: Boolean = false,
    val seenCount: Int = 0,
    val correctCount: Int = 0
)

/** 学习流水（用于统计：每日学习量、连续打卡、热力图） */
@Entity(tableName = "study_logs", indices = [Index("day"), Index("wordId")])
data class StudyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val day: Long,
    val timestamp: Long,
    /** study | review | quiz */
    val source: String,
    /** known | unknown */
    val result: String
)

/** 成就 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long? = null
)
