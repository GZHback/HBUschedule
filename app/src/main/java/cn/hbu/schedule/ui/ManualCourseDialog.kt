package cn.hbu.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.ManualEntry
import java.time.LocalDate

/** 周次怎么上：每周都上、只有单周、只有双周 */
private enum class WeekMode { ALL, ODD, EVEN }

/**
 * 排一条课：给教务里没排时间的课补时间，或者完全自建一门。
 *
 * [course] 是教务那门课时课程号跟着它，存下来以后重新抓课表还能对上号；
 * 传 null（或 [preset] 来自一门自建课）时课程名由学生自己填。
 */
@Composable
fun ManualCourseDialog(
    course: Course?,
    preset: ManualEntry?,
    onSave: (ManualEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val standalone = course == null || preset?.isStandalone == true
    val title = if (standalone) "添加课程" else "给「${course?.name}」排课"

    var name by remember { mutableStateOf(preset?.courseName ?: course?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(preset?.teacher ?: course?.teacher.orEmpty()) }
    var building by remember { mutableStateOf(preset?.building.orEmpty()) }
    var room by remember { mutableStateOf(preset?.room.orEmpty()) }
    var day by remember { mutableIntStateOf(preset?.day ?: defaultDay()) }
    var first by remember { mutableIntStateOf(preset?.firstSession ?: 1) }
    var last by remember { mutableIntStateOf(preset?.lastSession ?: 2) }
    var fromWeek by remember { mutableIntStateOf(preset?.weeks?.firstOrNull() ?: 1) }
    var toWeek by remember { mutableIntStateOf(preset?.weeks?.lastOrNull() ?: 20) }
    var mode by remember {
        mutableStateOf(
            preset?.weeks?.let { weeks ->
                val step = if (weeks.size > 1) weeks[1] - weeks[0] else 1
                when {
                    step > 1 && weeks.first() % 2 == 0 -> WeekMode.EVEN
                    step > 1 -> WeekMode.ODD
                    else -> WeekMode.ALL
                }
            } ?: WeekMode.ALL
        )
    }

    val weeks = remember(mode, fromWeek, toWeek) {
        val span = (fromWeek..toWeek).toList()
        when (mode) {
            WeekMode.ODD -> span.filter { it % 2 == 1 }
            WeekMode.EVEN -> span.filter { it % 2 == 0 }
            WeekMode.ALL -> span
        }
    }
    val nameMissing = standalone && name.isBlank()
    val noWeeks = weeks.isEmpty()

    FormScaffold(
        title = title,
        confirmLabel = "保存",
        canSave = !nameMissing && !noWeeks,
        onSave = {
            if (!nameMissing && !noWeeks) {
                onSave(
                    ManualEntry(
                        id = preset?.id ?: newId(),
                        // 挂到教务课程号上，下次抓课表才认得出是同一门
                        courseCode = if (standalone) "" else course?.code.orEmpty(),
                        courseName = name.trim(),
                        teacher = teacher.trim(),
                        day = day,
                        firstSession = first,
                        lastSession = last.coerceAtLeast(first),
                        weeks = weeks,
                        building = building.trim(),
                        room = room.trim(),
                    )
                )
            }
        },
        onCancel = onDismiss,
    ) {
        if (standalone) {
            FormField("课程名", name, { name = it }, placeholder = "比如 高等数学")
            FormField("老师（可不填）", teacher, { teacher = it })
        } else {
            // 挂在教务课程号上时，格子里显示的老师来自教务数据，这里改它不会生效，所以只给看、不给填
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (teacher.isNotBlank()) {
                    Text("老师 $teacher", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        FormSectionTitle("星期")
        ChipRow(BellSchedule.dayNames, selected = setOf(day - 1)) { day = it + 1 }

        FormSectionTitle("节次")
        StepperRow(
            "第",
            "$first 节",
            hint = BellSchedule.startOf(first),
            onMinus = {
                first = (first - 1).coerceAtLeast(1)
                if (last < first) last = first
            },
            onPlus = {
                first = (first + 1).coerceAtMost(BellSchedule.sessions.size)
                if (last < first) last = first
            },
        )
        StepperRow(
            "到第",
            "$last 节",
            hint = BellSchedule.endOf(last),
            onMinus = { last = (last - 1).coerceAtLeast(first) },
            onPlus = { last = (last + 1).coerceAtMost(BellSchedule.sessions.size) },
        )

        FormSectionTitle("周次")
        StepperRow(
            "从第",
            "$fromWeek 周",
            onMinus = {
                fromWeek = (fromWeek - 1).coerceAtLeast(1)
                if (toWeek < fromWeek) toWeek = fromWeek
            },
            onPlus = {
                fromWeek = (fromWeek + 1).coerceAtMost(30)
                if (toWeek < fromWeek) toWeek = fromWeek
            },
        )
        StepperRow(
            "到第",
            "$toWeek 周",
            onMinus = { toWeek = (toWeek - 1).coerceAtLeast(fromWeek) },
            onPlus = { toWeek = (toWeek + 1).coerceAtMost(30) },
        )
        ChipRow(
            listOf("每周都上", "单周", "双周"),
            selected = setOf(if (mode == WeekMode.ALL) 0 else if (mode == WeekMode.ODD) 1 else 2),
        ) { index ->
            mode = when (index) {
                1 -> WeekMode.ODD
                2 -> WeekMode.EVEN
                else -> WeekMode.ALL
            }
        }
        Text(
            if (noWeeks) "这个区间里没有符合条件的周，换一下起止周" else "上这些周：${weeks.joinToString(",")} 共 ${weeks.size} 周",
            fontSize = 13.sp,
            color = if (noWeeks) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        FormSectionTitle("地点（可不填）")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                FormField("教学楼", building, { building = it }, placeholder = "A3座")
            }
            Column(Modifier.weight(1f)) {
                FormField("教室", room, { room = it }, placeholder = "510")
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 打开表单时先落在今天，周几一般不用改 */
private fun defaultDay(): Int = LocalDate.now().dayOfWeek.value.coerceIn(1, 7)

/** 只用来认一条手动记录，时间戳够了；不自建 ID 体系，删掉就没了 */
private fun newId(): String = "m-" + System.currentTimeMillis()
