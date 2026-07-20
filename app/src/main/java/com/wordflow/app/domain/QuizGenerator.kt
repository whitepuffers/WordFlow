package com.wordflow.app.domain

import com.wordflow.app.data.db.entity.WordEntity
import com.wordflow.app.data.model.QuizMode

data class QuizQuestion(
    val wordId: Long,
    val word: String,
    val phonetic: String,
    val meaning: String,
    /** 选择/听力题的 4 个选项；填空题为空列表 */
    val options: List<String>,
    /** 正确选项下标；填空题为 -1 */
    val correctIndex: Int,
    /** 填空题的标准答案（即单词本身） */
    val answer: String
)

object QuizGenerator {

    const val OPTION_COUNT = 4

    /**
     * 从词池中生成 [count] 道题。词池少于 4 个词时返回空列表（无法构造干扰项）。
     */
    fun generate(mode: QuizMode, pool: List<WordEntity>, count: Int): List<QuizQuestion> {
        if (pool.size < OPTION_COUNT) return emptyList()

        val targets = pool.shuffled().take(count.coerceAtMost(pool.size))
        return targets.map { target ->
            when (mode) {
                QuizMode.CHOICE -> {
                    val distractors = pool.asSequence()
                        .filter { it.id != target.id && it.meaning != target.meaning }
                        .shuffled()
                        .take(OPTION_COUNT - 1)
                        .map { it.meaning }
                        .toList()
                    val options = (distractors + target.meaning).shuffled()
                    QuizQuestion(
                        wordId = target.id,
                        word = target.word,
                        phonetic = target.phonetic,
                        meaning = target.meaning,
                        options = options,
                        correctIndex = options.indexOf(target.meaning),
                        answer = target.word
                    )
                }

                QuizMode.LISTENING -> {
                    val distractors = pool.asSequence()
                        .filter { it.id != target.id }
                        .shuffled()
                        .take(OPTION_COUNT - 1)
                        .map { it.word }
                        .toList()
                    val options = (distractors + target.word).shuffled()
                    QuizQuestion(
                        wordId = target.id,
                        word = target.word,
                        phonetic = target.phonetic,
                        meaning = target.meaning,
                        options = options,
                        correctIndex = options.indexOf(target.word),
                        answer = target.word
                    )
                }

                QuizMode.FILL_BLANK -> QuizQuestion(
                    wordId = target.id,
                    word = target.word,
                    phonetic = target.phonetic,
                    meaning = target.meaning,
                    options = emptyList(),
                    correctIndex = -1,
                    answer = target.word
                )
            }
        }
    }
}
