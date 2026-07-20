package com.wordflow.app.domain

import kotlin.math.roundToInt

/**
 * SM-2 间隔重复算法（SuperMemo 2 的经典实现）。
 *
 * quality: 0~5 的掌握评分。本项目映射：
 *  - “认识”   -> [QUALITY_KNOWN] (4)
 *  - “不认识” -> [QUALITY_FORGOT] (1)
 *
 * 评分 < 3 视为遗忘：重复次数清零、明天重学、遗忘次数 +1。
 * 评分 >= 3：间隔按 1 天 -> 3 天 -> 上次的间隔 × 难度系数(EF) 递增，
 * EF 随评分微调且不低于 1.3。
 */
object SM2 {

    const val QUALITY_FORGOT = 1
    const val QUALITY_KNOWN = 4

    const val MIN_EASE = 1.3f
    const val DEFAULT_EASE = 2.5f

    data class State(
        val easeFactor: Float = DEFAULT_EASE,
        val intervalDays: Int = 0,
        val repetitions: Int = 0,
        val lapses: Int = 0
    )

    data class ScheduleResult(
        val state: State,
        /** 下次复习日期（epochDay） */
        val dueDay: Long,
        val mastered: Boolean
    )

    fun schedule(prev: State, quality: Int, todayEpochDay: Long): ScheduleResult {
        var ease = prev.easeFactor
        val interval: Int
        val repetitions: Int
        var lapses = prev.lapses

        if (quality < 3) {
            repetitions = 0
            interval = 1
            lapses += 1
        } else {
            repetitions = prev.repetitions + 1
            interval = when (repetitions) {
                1 -> 1
                2 -> 3
                else -> (prev.intervalDays * ease).roundToInt()
                    .coerceAtLeast(prev.intervalDays + 1)
            }
            ease = (ease + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f)))
                .coerceAtLeast(MIN_EASE)
        }

        // 连续 4 次记住且间隔达到两周，判定为“已掌握”
        val mastered = repetitions >= 4 && interval >= 14

        return ScheduleResult(
            state = State(ease, interval, repetitions, lapses),
            dueDay = todayEpochDay + interval,
            mastered = mastered
        )
    }
}
