package com.wordflow.app.data.repo

import android.content.Context
import com.wordflow.app.data.db.AppDatabase
import com.wordflow.app.data.db.entity.AchievementEntity
import com.wordflow.app.data.db.entity.WordBookEntity
import com.wordflow.app.data.db.entity.WordEntity
import com.wordflow.app.data.model.Books
import com.wordflow.app.data.model.WordDto
import com.wordflow.app.domain.Achievements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class WordRepository(
    private val context: Context,
    private val db: AppDatabase
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val seedMutex = Mutex()

    /**
     * 首次启动（或 assets 词库版本升级）时，把内置词库导入 Room。幂等，可安全重复调用。
     */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            for (def in Books.ALL) {
                val existing = db.wordDao().getBook(def.code)
                val count = db.wordDao().countWords(def.code)
                if (existing != null && existing.assetVersion >= def.assetVersion && count > 0) {
                    continue
                }
                val words = runCatching {
                    val text = context.assets.open(def.asset).bufferedReader().use { it.readText() }
                    json.decodeFromString<List<WordDto>>(text)
                }.getOrElse { emptyList() }

                if (words.isEmpty()) continue

                words.map { dto ->
                    WordEntity(
                        bookCode = def.code,
                        word = dto.word.trim().lowercase(),
                        phonetic = dto.phonetic.trim(),
                        meaning = dto.meaning.trim(),
                        example = dto.example.trim(),
                        exampleCn = dto.exampleCn.trim()
                    )
                }.chunked(500).forEach { chunk ->
                    db.wordDao().insertWords(chunk)
                }

                db.wordDao().upsertBooks(
                    listOf(
                        WordBookEntity(
                            code = def.code,
                            nameZh = def.nameZh,
                            nameEn = def.nameEn,
                            wordCount = db.wordDao().countWords(def.code),
                            assetVersion = def.assetVersion
                        )
                    )
                )
            }

            // 初始化成就表（未解锁状态）
            val achievementDao = db.achievementDao()
            if (achievementDao.all().isEmpty()) {
                achievementDao.upsertAll(Achievements.ALL.map { AchievementEntity(it.id) })
            }
        }
    }

    fun observeBooks() = db.wordDao().observeBooks()

    suspend fun getBook(code: String) = db.wordDao().getBook(code)

    suspend fun newWords(book: String, limit: Int) = db.wordDao().newWords(book, limit)

    suspend fun dueWords(book: String, today: Long, limit: Int) =
        db.wordDao().dueWords(book, today, limit)

    fun observeDueCount(book: String, today: Long) = db.wordDao().observeDueCount(book, today)

    suspend fun randomSeenWords(book: String, limit: Int) =
        db.wordDao().randomSeenWords(book, limit)

    suspend fun randomWords(book: String, limit: Int) = db.wordDao().randomWords(book, limit)

    suspend fun favoriteWords(book: String) = db.wordDao().favoriteWords(book)

    fun observeMastery(book: String) = db.wordDao().observeMastery(book)

    fun observeWordCount(book: String) = db.wordDao().observeWordCount(book)
}
