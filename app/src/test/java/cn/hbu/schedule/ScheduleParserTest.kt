package cn.hbu.schedule

import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.export.IcsExporter
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 全部断言基于 docs/sample_raw_schedule.json —— 一份真实接口响应脱敏后的样例。 */
class ScheduleParserTest {

    private val raw = javaClass.getResourceAsStream("/sample_raw_schedule.json")!!
        .bufferedReader().use { it.readText() }
    private val schedule = ScheduleParser.parse(raw)
    private val term = Term(LocalDate.of(2026, 8, 31), 20)

    private fun course(name: String) = schedule.courses.first { it.name == name }

    @Test
    fun `真实样例里的课程全部解析出来`() {
        assertEquals(21, schedule.courses.size)
        assertTrue(schedule.courses.all { it.name.isNotBlank() })
        assertEquals(24, schedule.courses.sumOf { it.meetings.size })
    }

    @Test
    fun `实验课没有排课时间但仍是一门课`() {
        // 教务页面上这 8 门只出现在「全部课程清单」里，课表格子里是空的
        val unscheduled = schedule.courses.filter { it.meetings.isEmpty() }.map { it.name }
        assertEquals(8, unscheduled.size)
        assertTrue("操作系统实验" in unscheduled)
        assertTrue("大学物理实验" in unscheduled)
    }

    @Test
    fun `周次以 classWeek 位图为准`() {
        assertEquals(listOf(1, 3, 5), ScheduleParser.weeksOfClassWeek("101010000000000000000000"))
        assertEquals(emptyList<Int>(), ScheduleParser.weeksOfClassWeek(null))
        assertEquals((2..16 step 2).toList(), course("大学英语3").meetings.first().weeks)
        assertEquals((1..9).toList(), course("工程制图与CAD").meetings.first().weeks)
    }

    @Test
    fun `continuingSession 是连堂节数`() {
        val meeting = course("大学生职业生涯规划（生涯发展）").meetings.first()
        assertEquals(9, meeting.firstSession)
        assertEquals(11, meeting.lastSession)
    }

    @Test
    fun `节次表是 11 节`() {
        assertEquals(11, BellSchedule.sessions.size)
        assertEquals("08:20", BellSchedule.startOf(1))
        assertEquals("21:35", BellSchedule.endOf(11))
        assertEquals("上午", BellSchedule.blockOf(4))
        assertEquals("下午", BellSchedule.blockOf(8))
        assertEquals("晚上", BellSchedule.blockOf(9))
    }

    @Test
    fun `学期周次能换算成具体日期`() {
        // 2026-09-28 是教务页面上显示的「第5周 星期一」
        assertEquals(5, term.weekOf(LocalDate.of(2026, 9, 28)) ?: -1)
        assertEquals(LocalDate.of(2026, 8, 31), term.dateOf(1, 1))
        assertEquals(LocalDate.of(2026, 9, 7), term.dateOf(1, 2))
        assertTrue(term.weekOf(LocalDate.of(2027, 6, 1)) == null)
    }

    @Test
    fun `断开或换步长的周次会拆成多条事件`() {
        assertEquals(
            listOf(listOf(1, 2, 3), listOf(5, 6), listOf(12)),
            IcsExporter.weekRuns(listOf(1, 2, 3, 5, 6, 12)),
        )
        assertEquals(listOf(listOf(2, 4, 6)), IcsExporter.weekRuns(listOf(2, 4, 6)))
    }

    @Test
    fun `ics 用浮动本地时间且双周课步长为 2`() {
        val ics = IcsExporter.export(schedule, term)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("DTSTART:20260831T082000"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=8"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=9"))
    }
}
