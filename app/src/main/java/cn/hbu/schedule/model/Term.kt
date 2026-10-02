package cn.hbu.schedule.model

import java.time.LocalDate

/**
 * 教务数据里的周次是相对说法，日历事件必须是具体日期，所以要把「第 N 周」换算成日期。
 * week1Monday 是第 1 周的周一。
 */
data class Term(val week1Monday: LocalDate, val totalWeeks: Int = 20) {

    /** 不管在不在学期内都算一个数：界面上要说清「按这个起点，今天落在第几周」 */
    fun weekNumber(date: LocalDate): Int =
        Math.floorDiv(date.toEpochDay() - week1Monday.toEpochDay(), 7L).toInt() + 1

    /** 学期外（寒暑假、起点设错）返回 null，让界面能如实说「今天不在本学期里」 */
    fun weekOf(date: LocalDate): Int? = weekNumber(date).takeIf { it in 1..totalWeeks }

    fun dateOf(day: Int, week: Int): LocalDate = week1Monday.plusDays((week - 1) * 7L + (day - 1))
}
