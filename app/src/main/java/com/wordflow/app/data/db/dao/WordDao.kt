package com.wordflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.wordflow.app.data.db.entity.WordBookEntity
import com.wordflow.app.data.db.entity.WordEntity
import kotlinx.coroutines.flow.Flow

data class StatusCount(val status: Int, val count: Int)

@Dao
interface WordDao {

    @Upsert
    suspend fun upsertBooks(books: List<WordBookEntity>)

    @Query("SELECT * FROM word_books")
    fun observeBooks(): Flow<List<WordBookEntity>>

    @Query("SELECT * FROM word_books WHERE code = :code")
    suspend fun getBook(code: String): WordBookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("SELECT COUNT(*) FROM words WHERE bookCode = :book")
    suspend fun countWords(book: String): Int

    @Query("SELECT COUNT(*) FROM words WHERE bookCode = :book")
    fun observeWordCount(book: String): Flow<Int>

    /** 取还没见过的新词（有学习记录的不再算新词） */
    @Query(
        """
        SELECT * FROM words w
        WHERE w.bookCode = :book
          AND w.id NOT IN (SELECT wordId FROM word_progress WHERE seenCount > 0)
        ORDER BY w.id
        LIMIT :limit
        """
    )
    suspend fun newWords(book: String, limit: Int): List<WordEntity>

    /** 到期待复习的词 */
    @Query(
        """
        SELECT w.* FROM words w
        JOIN word_progress p ON p.wordId = w.id
        WHERE w.bookCode = :book AND p.repetitions > 0 AND p.dueDay <= :today
        ORDER BY p.dueDay
        LIMIT :limit
        """
    )
    suspend fun dueWords(book: String, today: Long, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM word_progress p
        JOIN words w ON w.id = p.wordId
        WHERE w.bookCode = :book AND p.repetitions > 0 AND p.dueDay <= :today
        """
    )
    fun observeDueCount(book: String, today: Long): Flow<Int>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN word_progress p ON p.wordId = w.id
        WHERE w.bookCode = :book AND p.seenCount > 0
        ORDER BY RANDOM() LIMIT :limit
        """
    )
    suspend fun randomSeenWords(book: String, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE bookCode = :book ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWords(book: String, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT w.* FROM words w
        JOIN word_progress p ON p.wordId = w.id
        WHERE w.bookCode = :book AND p.favorite = 1
        ORDER BY w.word
        """
    )
    suspend fun favoriteWords(book: String): List<WordEntity>

    @Query(
        """
        SELECT p.status AS status, COUNT(*) AS count FROM word_progress p
        JOIN words w ON w.id = p.wordId
        WHERE w.bookCode = :book AND p.seenCount > 0
        GROUP BY p.status
        """
    )
    fun observeMastery(book: String): Flow<List<StatusCount>>
}
