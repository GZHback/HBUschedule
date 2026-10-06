// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.ui

import cn.hbu.schedule.data.ManualStore
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.ManualEntry
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「今天上什么」这一页的账，全是纯函数算的，所以能直接拿假时间点测。
 * 节次时间用仓库里那份真作息（第 1 节 08:20-09:05，第 4 节 11:15-12:00）。
 */
class TodayAgendaTest {

    private fun meeting(day: Int, first: Int, last: Int, weeks: List<Int> = (1..9).toList()) = Meeting(
        day = day,
        firstSession = first,
        lastSession = last,
        weeks = weeks,
        weekDescription = "1-9周",
        building = "A3座",
        room = "510",
    )

    private fun course(name: String, vararg meetings: Meeting) = Course(
        code = name,
        name = name,
        teacher = "样例教师",
        credits = 2.0,
        meetings = meetings.toList(),
    )

    @Test
    fun `时间字符串换成当天第几分钟`() {
        assertEquals(500, TodayAgenda.minutesOf("08:20"))
        assertEquals(720, TodayAgenda.minutesOf("12:00"))
        assertEquals(1295, TodayAgenda.minutesOf("21:35"))
        // 读不出来的一律按「还没上」处理，不能误判成已结束
        assertEquals(-1, TodayAgenda.minutesOf("坏"))
        assertEquals(-1, TodayAgenda.minutesOf("0820"))
    }

    @Test
    fun `上课状态按起止时间分三段`() {
        val schedule = Schedule(listOf(course("工程制图", meeting(day = 5, first = 1, last = 2))))
        // 第 1-2 节 = 08:20-10:00
        assertEquals(TodayAgenda.State.LATER, TodayAgenda.items(schedule, 5, 2, 8 * 60).first().state)
        assertEquals(TodayAgenda.State.NOW, TodayAgenda.items(schedule, 5, 2, 9 * 60).first().state)
        assertEquals(TodayAgenda.State.PAST, TodayAgenda.items(schedule, 5, 2, 10 * 60).first().state)
        assertEquals(TodayAgenda.State.PAST, TodayAgenda.items(schedule, 5, 2, 23 * 60).first().state)
    }

    @Test
    fun `只留今天这天这周真的有的课，按节次排好`() {
        val schedule = Schedule(
            listOf(
                course("晚上课", meeting(day = 5, first = 9, last = 11)),
                course("上午课", meeting(day = 5, first = 1, last = 2)),
                course("明天课", meeting(day = 6, first = 3, last = 4)),
                course("第10周才上", meeting(day = 5, first = 5, last = 6, weeks = (10..16).toList())),
            )
        )
        val names = TodayAgenda.items(schedule, day = 5, week = 2, nowMinutes = 0).map { it.course.name }
        assertEquals(listOf("上午课", "晚上课"), names)
    }

    @Test
    fun `下一节指的是第一节还没上完的课`() {
        val schedule = Schedule(
            listOf(
                course("第一节", meeting(day = 1, first = 1, last = 2)),
                course("第二节", meeting(day = 1, first = 3, last = 4)),
                course("第三节", meeting(day = 1, first = 5, last = 6)),
            )
        )
        val morning = TodayAgenda.items(schedule, 1, 1, 12 * 60)
        assertEquals(3, morning.size)
        // 中午 12 点：前两节都结束了，第三节（下午）是下一节
        assertEquals(2, TodayAgenda.nextIndex(morning))
        assertEquals(1, TodayAgenda.remaining(morning))

        // 全天上完就没有下一节，别把最后一节反复标成「下一节」
        val night = TodayAgenda.items(schedule, 1, 1, 22 * 60)
        assertEquals(-1, TodayAgenda.nextIndex(night))
        assertEquals(0, TodayAgenda.remaining(night))
    }

    @Test
    fun `手动加的课也进今天这一页`() {
        val schedule = Schedule(listOf(course("操作系统实验", meeting(day = 3, first = 1, last = 2))))
        val withManual = ManualStore.merged(
            schedule,
            listOf(
                ManualEntry(
                    id = "m1",
                    courseName = "雅思口语",
                    day = 3,
                    firstSession = 5,
                    lastSession = 6,
                    weeks = (1..18).toList(),
                    room = "线上",
                )
            ),
        )
        val names = TodayAgenda.items(withManual, 3, 3, 0).map { it.course.name }
        assertEquals(listOf("操作系统实验", "雅思口语"), names)
        val manual = TodayAgenda.items(withManual, 3, 3, 0).first { it.course.name == "雅思口语" }
        assertEquals("线上", manual.meeting.placeLabel)
    }

    @Test
    fun `课程号一样的手动课并进同一门，今天不会看到两遍`() {
        val schedule = Schedule(listOf(course("大学英语3", meeting(day = 1, first = 3, last = 4))))
        val merged = ManualStore.merged(
            schedule,
            listOf(
                ManualEntry(id = "m1", courseCode = "大学英语3", courseName = "大学英语3", day = 1, firstSession = 9, lastSession = 9, weeks = (1..18).toList()),
            ),
        )
        val items = TodayAgenda.items(merged, 1, 1, 0)
        // 两段时间块，但只有一门课
        assertEquals(2, items.size)
        assertEquals(1, merged.courses.count { it.name == "大学英语3" })
        assertEquals(listOf(3, 9), items.map { it.meeting.firstSession })
    }
}
