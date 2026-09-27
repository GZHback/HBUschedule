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
) {
    val location: String
        get() = listOf(campus, building, room).filter { it.isNotBlank() }.joinToString(" ")

    /** 格子里地方窄，只显示楼和教室 */
    val placeLabel: String
        get() = listOf(building, room).filter { it.isNotBlank() }.joinToString(" ")
}

data class Course(
    val code: String,
    val name: String,
    val teacher: String,
    val credits: Double,
    val meetings: List<Meeting>,
)

data class Schedule(val courses: List<Course>) {

    /** 某一天某一周实际上课的门次，按起始节次排序，用于画整块课程。 */
    fun meetingsOn(day: Int, week: Int): List<Pair<Course, Meeting>> =
        courses
            .flatMap { course -> course.meetings.filter { it.day == day && week in it.weeks } .map { course to it } }
            .sortedBy { (_, meeting) -> meeting.firstSession }
}
