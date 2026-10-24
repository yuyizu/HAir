package dev.hair.app.data

import java.time.LocalDate

enum class Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    /** 另一个半球同一天的季节：整体错开半年。 */
    val opposite: Season get() = entries[(ordinal + 2) % entries.size]
}

/** 气象季节：北半球 3–5 月是春，6–8 月夏，9–11 月秋，12–2 月冬；南半球同一天是相反的季节。 */
fun seasonOf(date: LocalDate, northern: Boolean = true): Season {
    val season = when (date.monthValue) {
        in 3..5 -> Season.SPRING
        in 6..8 -> Season.SUMMER
        in 9..11 -> Season.AUTUMN
        else -> Season.WINTER
    }
    return if (northern) season else season.opposite
}
