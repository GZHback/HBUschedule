package cn.hbu.schedule.model

data class Meeting(
    val day: Int,
    val firstSession: Int,
    val lastSession: Int,
    val weeks: List<Int>,
    val weekDescription: String,
    val location: String,
)

data class Course(
    val code: String,
    val name: String,
    val teacher: String,
    val credits: Double,
    val meetings: List<Meeting>,
)

data class Schedule(val courses: List<Course>) {

    data class Cell(val course: Course, val meeting: Meeting, val startsHere: Boolean)

    fun cellFor(day: Int, session: Int, week: Int?): List<Cell> =
        courses.flatMap { course ->
            course.meetings
                .filter {
                    it.day == day && session in it.firstSession..it.lastSession &&
                        (week == null || week in it.weeks)
                }
                .map { Cell(course, it, session == it.firstSession) }
        }
}
