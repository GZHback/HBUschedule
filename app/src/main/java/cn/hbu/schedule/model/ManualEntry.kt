package cn.hbu.schedule.model

/**
 * 学生自己排的一条课，存在本机，重新抓课表不会丢。
 *
 * 两种用法：[courseCode] 是教务里的课程号时，它给那门没有 timeAndPlaceList 的课（实验、
 * 课程设计）补上时间；[courseCode] 为空时是一门完全自建的课，名字老师都由学生填。
 */
data class ManualEntry(
    val id: String,
    val courseCode: String = "",
    val courseName: String,
    val teacher: String = "",
    /** 1=周一 .. 7=周日，和 Meeting.day 同一套编号 */
    val day: Int,
    val firstSession: Int,
    val lastSession: Int,
    val weeks: List<Int>,
    val building: String = "",
    val room: String = "",
) {

    /** 自建课在合并时按这个名字归到同一门课，手动补的课则直接挂到教务那门上 */
    val isStandalone: Boolean
        get() = courseCode.isBlank()

    val courseKey: String
        get() = courseCode.ifBlank { courseName.trim() }

    /** 手动排的课没有 weekDescription，按选出来的周次自己拼一句给人看 */
    val weekLabel: String
        get() = when {
            weeks.isEmpty() -> ""
            weeks.size == 1 -> "第${weeks.first()}周"
            else -> {
                val step = weeks[1] - weeks[0]
                val parity = if (step > 1) if (weeks.first() % 2 == 1) " 单周" else " 双周" else ""
                "第${weeks.first()}-${weeks.last()}周$parity"
            }
        }

    val sessionLabel: String
        get() = if (firstSession == lastSession) "第$firstSession 节" else "第$firstSession-$lastSession 节"

    val placeLabel: String
        get() = listOf(building, room).filter { it.isNotBlank() }.joinToString(" ")

    fun toMeeting(): Meeting = Meeting(
        day = day,
        firstSession = firstSession,
        lastSession = lastSession,
        weeks = weeks,
        weekDescription = weekLabel,
        building = building,
        room = room,
        manualId = id,
    )

    companion object {

        /** 起始、结束、步长拼成周次列表；步长 2 就是从 [start] 起隔周 */
        fun weeksInRange(start: Int, end: Int, step: Int = 1): List<Int> {
            if (start <= 0 || end < start) return emptyList()
            val by = if (step <= 0) 1 else step
            return (generateSequence(start) { it + by }.takeWhile { it <= end }).toList()
        }
    }
}
