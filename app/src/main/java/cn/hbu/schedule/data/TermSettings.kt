// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.data

import cn.hbu.schedule.model.Term
import java.time.LocalDate

/**
 * 学期起点存本机。第 1 周周一是唯一决定「今天是第几周」的数，写死在代码里就意味着
 * 换学期时必须发版，所以它得能改。
 *
 * 默认值是核对过的：2026-09-28 教务页面显示「第 5 周 星期一」，按 2026-08-31 起算正好第 5 周。
 */
data class TermSettings(val week1Monday: LocalDate, val totalWeeks: Int) {

    fun toTerm(): Term = Term(week1Monday, totalWeeks)

    companion object {

        val DEFAULT = TermSettings(LocalDate.of(2026, 8, 31), 20)

        fun encode(settings: TermSettings): String = "${settings.week1Monday}|${settings.totalWeeks}"

        /** 存坏了、版本旧、日期不合法都退回默认值，不能因为设置读不出来就没有课表 */
        fun decode(text: String?): TermSettings {
            val parts = text?.trim()?.split("|") ?: return DEFAULT
            if (parts.size != 2) return DEFAULT
            val monday = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return DEFAULT
            val weeks = parts[1].trim().toIntOrNull() ?: return DEFAULT
            if (weeks !in 1..30) return DEFAULT
            return TermSettings(monday, weeks)
        }
    }
}

/**
 * 教务课表页 HTML 里可能有它自己标的当前周次。读到它就能反推第 1 周周一，
 * 换学期、学校改校历都不用等我们发版。
 *
 * 只认带「本周 / 当前」这类限定词的写法：课表格子里本身就到处是「第 7 周」这种文案，
 * 拿它当周次锚点会把正确的设置改错。
 */
object SchoolWeekProbe {

    private val patterns = listOf(
        Regex("""(?:本周|当前周次|当前周|本教学周)[^0-9\n<>]{0,12}(\d{1,2})"""),
        Regex("""(?i)(?:currentweek|current_week|bxzc|zxzc)[^0-9\n<>]{0,8}(\d{1,2})"""),
    )

    /** 读不到就返回 null，交回学生自己设的值 */
    fun detect(html: String?): Int? {
        if (html.isNullOrBlank()) return null
        patterns.forEach { p ->
            val hit = p.find(html)?.groupValues?.get(1)?.trim()?.toIntOrNull()
            if (hit != null && hit in 1..30) return hit
        }
        return null
    }

    /** 任何日期都往前收到本周一，保证起点真的是周一 */
    fun alignToMonday(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** 「[detectedOn] 那天是第 [schoolWeek] 周」倒推第 1 周周一 */
    fun week1MondayOf(detectedOn: LocalDate, schoolWeek: Int): LocalDate =
        alignToMonday(detectedOn).minusWeeks((schoolWeek - 1).coerceAtLeast(0).toLong())
}

/** 上次抓取时从教务页面读到的周次，以及读到的那天 */
data class SchoolWeekHint(val week: Int, val detectedOn: LocalDate) {

    /** 隔太久的提示没有意义，学生可能已经换学期了 */
    fun isFresh(today: LocalDate): Boolean = !detectedOn.isAfter(today) && today.toEpochDay() - detectedOn.toEpochDay() <= 14

    companion object {
        fun encode(hint: SchoolWeekHint): String = "${hint.week}|${hint.detectedOn}"

        fun decode(text: String?): SchoolWeekHint? {
            val parts = text?.trim()?.split("|") ?: return null
            if (parts.size != 2) return null
            val week = parts[0].trim().toIntOrNull() ?: return null
            val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
            return SchoolWeekHint(week, date)
        }
    }
}

/**
 * 教务页面自己标的周次和按当前设置算出来的不一致 —— 这是唯一能发现「学期起点差了一周」的线索。
 *
 * 只提示、不当场覆盖：那个数是从 HTML 正则来的，万一匹配到页面上别的数字，
 * 把本来对的设置改错就亏大了，所以让学生看一眼自己点。
 */
data class Calibration(
    val schoolWeek: Int,
    val detectedOn: LocalDate,
    val week1Monday: LocalDate,
    val ourWeek: Int?,
) {
    companion object {

        /** 没读到、读到的和算出来一样、或者提示放太久，都不该打扰学生 */
        fun between(hint: SchoolWeekHint?, settings: TermSettings, today: LocalDate): Calibration? {
            if (hint == null || !hint.isFresh(today)) return null
            val ourWeek = settings.toTerm().weekOf(hint.detectedOn)
            if (ourWeek == hint.week) return null
            return Calibration(
                schoolWeek = hint.week,
                detectedOn = hint.detectedOn,
                week1Monday = SchoolWeekProbe.week1MondayOf(hint.detectedOn, hint.week),
                ourWeek = ourWeek,
            )
        }
    }
}
