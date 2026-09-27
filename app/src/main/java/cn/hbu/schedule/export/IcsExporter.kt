package cn.hbu.schedule.export

import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object IcsExporter {

    private val basic = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(schedule: Schedule, term: Term): String {
        val stamp = LocalDateTime.now(ZoneOffset.UTC).format(basic) + "Z"
        val out = StringBuilder()
        line(out, "BEGIN:VCALENDAR")
        line(out, "VERSION:2.0")
        line(out, "PRODID:-//HBUschedule//CN")
        line(out, "CALSCALE:GREGORIAN")
        schedule.courses.forEach { course ->
            course.meetings.forEach { meeting ->
                weekRuns(meeting.weeks).forEachIndexed { index, run ->
                    event(out, course, meeting, run, index, term, stamp)
                }
            }
        }
        line(out, "END:VCALENDAR")
        return out.toString()
    }

    private fun event(
        out: StringBuilder,
        course: Course,
        meeting: Meeting,
        run: List<Int>,
        index: Int,
        term: Term,
        stamp: String,
    ) {
        val date = term.dateOf(meeting.day, run.first())
        val step = if (run.size > 1) run[1] - run[0] else 1
        line(out, "BEGIN:VEVENT")
        line(out, "UID:${course.code}-${meeting.day}-${meeting.firstSession}-$index@hbuschedule")
        line(out, "DTSTAMP:$stamp")
        // 不带 Z 也不带 TZID 的浮动本地时间：日历按设备所在时区解释，正好贴合校内作息
        line(out, "DTSTART:${date.atTime(timeOf(BellSchedule.startOf(meeting.firstSession))).format(basic)}")
        line(out, "DTEND:${date.atTime(timeOf(BellSchedule.endOf(meeting.lastSession))).format(basic)}")
        if (run.size > 1) line(out, "RRULE:FREQ=WEEKLY;INTERVAL=$step;COUNT=${run.size}")
        line(out, "SUMMARY:${escape(course.name)}")
        line(out, "LOCATION:${escape(meeting.location)}")
        line(out, "DESCRIPTION:${escape("${course.teacher}｜${meeting.weekDescription}".trim('｜'))}")
        line(out, "END:VEVENT")
    }

    /**
     * 把周次拆成步长 1 或 2 的连续等差段，一段一条 RRULE。
     * 双周课步长为 2；断开或换步长（比如 1-9 周和第 12 周）就另起一条事件。
     */
    fun weekRuns(weeks: List<Int>): List<List<Int>> {
        val sorted = weeks.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val runs = ArrayList<List<Int>>()
        var run = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val step = sorted[i] - sorted[i - 1]
            val continues = run.size == 1 || step == sorted[i - 1] - sorted[i - 2]
            if (step in 1..2 && continues) {
                run.add(sorted[i])
            } else {
                runs.add(run)
                run = mutableListOf(sorted[i])
            }
        }
        runs.add(run)
        return runs
    }

    private fun timeOf(text: String) = LocalTime.of(text.substring(0, 2).toInt(), text.substring(3, 5).toInt())

    private fun escape(text: String) = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    /** RFC 5545 要求物理行不超过 75 字节，中文按字节数折行，续行以空格开头。 */
    private fun line(out: StringBuilder, content: String) {
        val parts = ArrayList<String>()
        var current = StringBuilder()
        for (ch in content) {
            if (StringBuilder(current).append(ch).toString().toByteArray(Charsets.UTF_8).size > 73) {
                parts.add(current.toString())
                current = StringBuilder(" ")
            }
            current.append(ch)
        }
        parts.add(current.toString())
        out.append(parts.joinToString("\r\n")).append("\r\n")
    }
}
