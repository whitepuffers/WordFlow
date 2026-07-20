package com.wordflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wordflow.app.data.db.entity.WordProgressEntity

@Dao
interface ProgressDao {

    @Query("SELECT * FROM word_progress WHERE wordId = :wordId")
    suspend fun get(wordId: Long): WordProgressEntity?

    @Upsert
    suspend fun upsert(progress: WordProgressEntity)

    @Query("UPDATE word_progress SET favorite = :fav WHERE wordId = :wordId")
    suspend fun setFavorite(wordId: Long, fav: Boolean)

    @Query("SELECT COUNT(*) FROM word_progress WHERE status = 2")
    suspend fun masteredCount(): Int

    @Query("SELECT COUNT(*) FROM word_progress WHERE favorite = 1")
    suspend fun favoriteCount(): Int

    @Query("SELECT COUNT(*) FROM word_progress WHERE seenCount > 0")
    suspend fun seenCount(): Int

    @Query("DELETE FROM word_progress")
    suspend fun clearAll()
}
