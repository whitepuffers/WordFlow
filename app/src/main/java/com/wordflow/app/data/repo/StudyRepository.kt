package com.wordflow.app.data.repo

import com.wordflow.app.data.db.AppDatabase
import com.wordflow.app.data.db.entity.AchievementEntity
import com.wordflow.app.data.db.entity.StudyLogEntity
import com.wordflow.app.data.db.entity.WordProgressEntity
import com.wordflow.app.data.model.StudySource
import com.wordflow.app.domain.AchievementDef
import com.wordflow.app.domain.Achievements
import com.wordflow.app.domain.SM2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class StudyRepository(private val db: AppDatabase) {

    private fun today(): Long = LocalDate.now().toEpochDay()

    /**
     * 记录一次学习/复习/测试结果，并按 SM-2 更新该词的复习计划。
     * @return 本次操作新解锁的成就（可能为空）
     */
    suspend fun applyResult(
        wordId: Long,
        known: Boolean,
        source: StudySource
    ): List<AchievementDef> = withContext(Dispatchers.IO) {
        val day = today()
        val quality = if (known) SM2.QUALITY_KNOWN else SM2.QUALITY_FORGOT

        val prev = db.progressDao().get(wordId)
        val prevState = SM2.State(
            easeFactor = prev?.easeFactor ?: SM2.DEFAULT_EASE,
            intervalDays = prev?.intervalDays ?: 0,
            repetitions = prev?.repetitions ?: 0,
            lapses = prev?.lapses ?: 0
        )
        val result = SM2.schedule(prevState, quality, day)

        db.progressDao().upsert(
            WordProgressEntity(
                wordId = wordId,
                status = if (result.mastered) 2 else 1,
                easeFactor = result.state.easeFactor,
                intervalDays = result.state.intervalDays,
                repetitions = result.state.repetitions,
                lapses = result.state.lapses,
                dueDay = result.dueDay,
                lastReviewDay = day,
                favorite = prev?.favorite ?: false,
                seenCount = (prev?.seenCount ?: 0) + 1,
                correctCount = (prev?.correctCount ?: 0) + if (known) 1 else 0
            )
        )

        db.studyLogDao().insert(
            StudyLogEntity(
                wordId = wordId,
                day = day,
                timestamp = System.currentTimeMillis(),
                source = source.raw,
                result = if (known) "known" else "unknown"
            )
        )

        checkAchievements(quizFinished = false, quizPerfect = false)
    }

    /** 切换收藏状态；返回新解锁成就 */
    suspend fun toggleFavorite(wordId: Long): List<AchievementDef> = withContext(Dispatchers.IO) {
        val prev = db.progressDao().get(wordId)
        val newFav = !(prev?.favorite ?: false)
        if (prev == null) {
            db.progressDao().upsert(WordProgressEntity(wordId = wordId, favorite = true))
        } else {
            db.progressDao().setFavorite(wordId, newFav)
        }
        checkAchievements(quizFinished = false, quizPerfect = false)
    }

    /** 评估全部成就条件，解锁满足条件的项；返回本次新解锁的成就 */
    suspend fun checkAchievements(
        quizFinished: Boolean,
        quizPerfect: Boolean
    ): List<AchievementDef> = withContext(Dispatchers.IO) {
        val dao = db.achievementDao()
        val now = System.currentTimeMillis()
        val newly = mutableListOf<AchievementDef>()

        suspend fun tryUnlock(id: String, condition: Boolean) {
            if (!condition) return
            if (dao.unlock(id, now) > 0) {
                Achievements.byId(id)?.let { newly.add(it) }
            }
        }

        val totalLogs = db.studyLogDao().totalCount()
        val mastered = db.progressDao().masteredCount()
        val favorites = db.progressDao().favoriteCount()
        val streak = computeStreak(db.studyLogDao().observeActiveDays().first(), today())

        tryUnlock("first_study", totalLogs > 0)
        tryUnlock("streak_3", streak >= 3)
        tryUnlock("streak_7", streak >= 7)
        tryUnlock("streak_30", streak >= 30)
        tryUnlock("mastered_100", mastered >= 100)
        tryUnlock("mastered_500", mastered >= 500)
        tryUnlock("learned_1000", totalLogs >= 1000)
        tryUnlock("quiz_first", quizFinished)
        tryUnlock("quiz_perfect", quizPerfect)
        tryUnlock("favorite_50", favorites >= 50)

        newly
    }

    /** 今日已学单词数（去重） */
    fun observeTodayCount(): Flow<Int> = db.studyLogDao().observeDayCount(today())

    suspend fun todayCount(): Int = db.studyLogDao().countForDay(today())

    /** 连续打卡天数（今天还没学则从昨天起算） */
    fun observeStreak(): Flow<Int> =
        db.studyLogDao().observeActiveDays().map { computeStreak(it, today()) }

    /** 近 [days] 天每天学习单词数 */
    fun observeDailyCounts(days: Int) =
        db.studyLogDao().observeDailyCounts(today() - days + 1L)

    fun observeAchievements() = db.achievementDao().observeAll()

    /** 清空全部学习数据（进度/流水/成就），词库保留 */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        db.progressDao().clearAll()
        db.studyLogDao().clearAll()
        db.achievementDao().clearAll()
        db.achievementDao().upsertAll(Achievements.ALL.map { AchievementEntity(it.id) })
    }

    private fun computeStreak(activeDays: List<Long>, today: Long): Int {
        if (activeDays.isEmpty()) return 0
        val days = activeDays.toSet()
        var cursor = today
        if (!days.contains(cursor)) cursor -= 1 // 今天还没学，从昨天开始算
        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor -= 1
        }
        return streak
    }
}
