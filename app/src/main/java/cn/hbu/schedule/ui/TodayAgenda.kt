package cn.hbu.schedule.ui

import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule

/**
 * 「今天要上哪些课、哪节还没上」的判断，全部是不碰界面的纯函数。
 *
 * 单独放一个文件是因为 Compose 那边 CI 只能证明编得过、证不明算得对；
 * 这几个函数能在 JVM 上直接拿假时间点测。
 */
object TodayAgenda {

    enum class State { PAST, NOW, LATER }

    data class Item(val course: Course, val meeting: Meeting, val state: State)

    /** "08:20" -> 500 分钟；读不出来的按「还没上」处理，不能误判成已结束 */
    fun minutesOf(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        return h * 60 + m
    }

    /**
     * 某一天某一周实际要上的课，按起始节次排好。
     *
     * [day] 是 1=周一 .. 7=周日，[nowMinutes] 是当天的第几分钟。
     */
    fun items(schedule: Schedule, day: Int, week: Int, nowMinutes: Int): List<Item> =
        schedule.meetingsOn(day, week).map { (course, meeting) ->
            val start = minutesOf(BellSchedule.startOf(meeting.firstSession))
            val end = minutesOf(BellSchedule.endOf(meeting.lastSession))
            val state = when {
                start < 0 || end < 0 -> State.LATER
                nowMinutes >= end -> State.PAST
                nowMinutes >= start -> State.NOW
                else -> State.LATER
            }
            Item(course, meeting, state)
        }

    /** 高亮哪一条：第一节还没上完的课。全是 PAST 就没有下一节 */
    fun nextIndex(items: List<Item>): Int =
        items.indexOfFirst { it.state != State.PAST }

    fun label(state: State): String = when (state) {
        State.PAST -> "已结束"
        State.NOW -> "正在上"
        State.LATER -> "还没上"
    }

    /** 还剩几节没上完，给标题旁边那句「还有 2 节」用 */
    fun remaining(items: List<Item>): Int = items.count { it.state != State.PAST }
}
