package com.wordflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wordflow.app.data.db.entity.StudyLogEntity
import kotlinx.coroutines.flow.Flow

data class DayCount(val day: Long, val count: Int)

@Dao
interface StudyLogDao {

    @Insert
    suspend fun insert(log: StudyLogEntity)

    @Query("SELECT COUNT(DISTINCT wordId) FROM study_logs WHERE day = :day")
    fun observeDayCount(day: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT wordId) FROM study_logs WHERE day = :day")
    suspend fun countForDay(day: Long): Int

    @Query("SELECT COUNT(*) FROM study_logs")
    suspend fun totalCount(): Int

    @Query("SELECT DISTINCT day FROM study_logs ORDER BY day DESC")
    fun observeActiveDays(): Flow<List<Long>>

    @Query(
        """
        SELECT day AS day, COUNT(DISTINCT wordId) AS count
        FROM study_logs
        WHERE day >= :since
        GROUP BY day
        ORDER BY day
        """
    )
    fun observeDailyCounts(since: Long): Flow<List<DayCount>>

    @Query("DELETE FROM study_logs")
    suspend fun clearAll()
}
