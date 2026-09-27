package cn.hbu.schedule.model

/** 河北大学作息表，取自教务课表页面渲染出的真实时间，共 11 节。 */
object BellSchedule {

    val times: Map<Int, Pair<String, String>> = linkedMapOf(
        1 to ("08:20" to "09:05"),
        2 to ("09:15" to "10:00"),
        3 to ("10:20" to "11:05"),
        4 to ("11:15" to "12:00"),
        5 to ("14:30" to "15:15"),
        6 to ("15:25" to "16:10"),
        7 to ("16:20" to "17:05"),
        8 to ("17:15" to "18:00"),
        9 to ("19:00" to "19:45"),
        10 to ("19:55" to "20:40"),
        11 to ("20:50" to "21:35"),
    )

    val sessions: List<Int> get() = times.keys.sorted()

    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun startOf(session: Int) = times.getValue(session).first

    fun endOf(session: Int) = times.getValue(session).second

    fun blockOf(session: Int) = when (session) {
        in 1..4 -> "上午"
        in 5..8 -> "下午"
        else -> "晚上"
    }
}
