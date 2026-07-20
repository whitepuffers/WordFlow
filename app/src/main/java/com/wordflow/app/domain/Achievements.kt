package com.wordflow.app.domain

data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val emoji: String
)

object Achievements {

    val ALL = listOf(
        AchievementDef("first_study", "初次启程", "完成第一次学习", "🌱"),
        AchievementDef("streak_3", "三日坚持", "连续打卡 3 天", "🔥"),
        AchievementDef("streak_7", "七日之约", "连续打卡 7 天", "🗓️"),
        AchievementDef("streak_30", "月度达人", "连续打卡 30 天", "👑"),
        AchievementDef("mastered_100", "百词斩", "累计掌握 100 个单词", "⚔️"),
        AchievementDef("mastered_500", "五百精通", "累计掌握 500 个单词", "🏅"),
        AchievementDef("learned_1000", "千词之旅", "累计学习 1000 词次", "🚀"),
        AchievementDef("quiz_first", "初试牛刀", "完成第一次测试", "📝"),
        AchievementDef("quiz_perfect", "百发百中", "一次测试全部答对", "🎯"),
        AchievementDef("favorite_50", "收藏达人", "收藏 50 个单词", "⭐")
    )

    fun byId(id: String): AchievementDef? = ALL.firstOrNull { it.id == id }
}
