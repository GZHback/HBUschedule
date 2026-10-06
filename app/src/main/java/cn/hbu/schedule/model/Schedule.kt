// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.model

data class Meeting(
    val day: Int,
    val firstSession: Int,
    val lastSession: Int,
    val weeks: List<Int>,
    val weekDescription: String,
    val campus: String = "",
    val building: String = "",
    val room: String = "",
    /** 手动排的课带着它那条记录的 id，点详情才认得出该删谁；教务抓来的为 null */
    val manualId: String? = null,
) {
    val location: String
        get() = listOf(campus, building, room).filter { it.isNotBlank() }.joinToString(" ")

    /** 格子里地方窄，只显示楼和教室 */
    val placeLabel: String
        get() = listOf(building, room).filter { it.isNotBlank() }.joinToString(" ")

    val sessionsLabel: String
        get() = if (firstSession == lastSession) "第$firstSession 节" else "第$firstSession-$lastSession 节"
}

data class Course(
    val code: String,
    val name: String,
    val teacher: String,
    val credits: Double,
    val examType: String = "",
    val category: String = "",
    val property: String = "",
    val meetings: List<Meeting>,
)

data class Schedule(val courses: List<Course>) {

    /** 某一天某一周实际上课的门次，按起始节次排序，用于画整块课程。 */
    fun meetingsOn(day: Int, week: Int): List<Pair<Course, Meeting>> =
        courses
            .flatMap { course -> course.meetings.filter { it.day == day && week in it.weeks } .map { course to it } }
            .sortedBy { (_, meeting) -> meeting.firstSession }
}
