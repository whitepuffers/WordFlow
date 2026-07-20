package com.wordflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wordflow.app.data.db.entity.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun all(): List<AchievementEntity>

    @Upsert
    suspend fun upsertAll(items: List<AchievementEntity>)

    /** 仅当未解锁时写入解锁时间；返回受影响行数（>0 表示本次新解锁） */
    @Query("UPDATE achievements SET unlockedAt = :time WHERE id = :id AND unlockedAt IS NULL")
    suspend fun unlock(id: String, time: Long): Int

    @Query("DELETE FROM achievements")
    suspend fun clearAll()
}
