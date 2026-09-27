package cn.hbu.schedule.model

import java.time.LocalDate

/**
 * 教务数据里的周次是相对说法，日历事件必须是具体日期，所以要把「第 N 周」换算成日期。
 * week1Monday 是第 1 周的周一。
 */
data class Term(val week1Monday: LocalDate, val totalWeeks: Int = 20) {

    fun weekOf(date: LocalDate): Int? {
        val week = Math.floorDiv(date.toEpochDay() - week1Monday.toEpochDay(), 7L).toInt() + 1
        return if (week in 1..totalWeeks) week else null
    }

    fun dateOf(day: Int, week: Int): LocalDate = week1Monday.plusDays((week - 1) * 7L + (day - 1))
}
