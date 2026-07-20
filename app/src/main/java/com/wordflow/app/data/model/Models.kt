package com.wordflow.app.data.model

import kotlinx.serialization.Serializable

/** assets/words 目录下 JSON 文件中单个词条的结构 */
@Serializable
data class WordDto(
    val word: String,
    val phonetic: String = "",
    val meaning: String,
    val example: String = "",
    val exampleCn: String = ""
)

enum class MasteryStatus(val code: Int, val label: String) {
    NEW(0, "新学"),
    LEARNING(1, "学习中"),
    MASTERED(2, "已掌握");

    companion object {
        fun from(code: Int): MasteryStatus = entries.firstOrNull { it.code == code } ?: NEW
    }
}

enum class StudySource(val raw: String) {
    STUDY("study"),
    REVIEW("review"),
    QUIZ("quiz")
}

enum class ThemeMode(val raw: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(raw: String?): ThemeMode = entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

enum class QuizMode(val title: String, val desc: String) {
    CHOICE("选择题", "看英文单词，选出正确中文释义"),
    FILL_BLANK("填空题", "看中文释义，默写出英文单词"),
    LISTENING("听力题", "听发音，选出正确的单词")
}

/** 内置词库定义。assetVersion 递增会触发重新导入。 */
data class BookDef(
    val code: String,
    val nameZh: String,
    val nameEn: String,
    val asset: String,
    val assetVersion: Int
)

object Books {
    val ALL = listOf(
        BookDef("cet4", "四级词汇", "CET-4", "words/cet4.json", assetVersion = 1),
        BookDef("cet6", "六级词汇", "CET-6", "words/cet6.json", assetVersion = 1),
        BookDef("toefl", "托福词汇", "TOEFL", "words/toefl.json", assetVersion = 1),
        BookDef("ielts", "雅思词汇", "IELTS", "words/ielts.json", assetVersion = 1)
    )

    fun byCode(code: String): BookDef = ALL.firstOrNull { it.code == code } ?: ALL.first()
}
