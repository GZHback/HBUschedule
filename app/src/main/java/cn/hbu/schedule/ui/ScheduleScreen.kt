package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDate

private const val SESSION_COUNT = 11
private val SlotHeight = 50.dp
private val DayWidth = 78.dp
private val TimeWidth = 50.dp
private val HeaderHeight = 22.dp

private val CourseColors = listOf(
    Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFF5C6BC0), Color(0xFF66BB6A),
    Color(0xFFEF5350), Color(0xFFEF6C00), Color(0xFF29B6F6), Color(0xFFAB47BC),
    Color(0xFFEC407A), Color(0xFF7CB342),
)

@Composable
fun ScheduleScreen(schedule: Schedule, term: Term) {
    val today = LocalDate.now()
    val thisWeek = term.weekOf(today)
    var week by remember { mutableStateOf(thisWeek ?: 1) }
    var selected by remember { mutableStateOf<Pair<Course, Meeting>?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("河北大学", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(
                    "◀",
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { week = (week - 1).coerceAtLeast(1) }.padding(10.dp),
                )
                Text(
                    text = "第 $week 周" + if (week == thisWeek) " · 本周" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 100.dp),
                )
                Text(
                    "▶",
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { week = (week + 1).coerceAtMost(term.totalWeeks) }.padding(10.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                // 时间轴固定在左侧，只有课表格子横向滚动
                Column(Modifier.width(TimeWidth)) {
                    Spacer(Modifier.height(HeaderHeight))
                    BellSchedule.sessions.forEach { session ->
                        Column(Modifier.height(SlotHeight)) {
                            Text("第 $session 节", fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BellSchedule.startOf(session), fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (1..7).forEach { day ->
                        DayColumn(
                            schedule = schedule,
                            day = day,
                            week = week,
                            isToday = day == today.dayOfWeek.value && week == thisWeek,
                            modifier = Modifier.width(DayWidth),
                            onPick = { course, meeting -> selected = course to meeting },
                        )
                    }
                }
            }

            UnscheduledSection(schedule)
        }
    }

    selected?.let { (course, meeting) ->
        CourseDetail(course = course, meeting = meeting, onDismiss = { selected = null })
    }
}

@Composable
private fun DayColumn(
    schedule: Schedule,
    day: Int,
    week: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onPick: (Course, Meeting) -> Unit,
) {
    Column(modifier.padding(horizontal = 2.dp)) {
        Text(
            text = BellSchedule.dayNames[day - 1],
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(HeaderHeight),
        )
        Box(Modifier.fillMaxWidth().height(SlotHeight * SESSION_COUNT)) {
            schedule.meetingsOn(day, week).forEach { (course, meeting) ->
                val span = meeting.lastSession - meeting.firstSession + 1
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = SlotHeight * (meeting.firstSession - 1))
                        .height(SlotHeight * span - 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(courseColor(course))
                        .clickable { onPick(course, meeting) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        course.name,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        course.teacher,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = Color(0xE6FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        meeting.placeLabel,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = Color(0xCCFFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseDetail(course: Course, meeting: Meeting, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name, fontSize = 17.sp) },
        text = {
            Column {
                DetailRow("教师", course.teacher)
                DetailRow(
                    "时间",
                    "${BellSchedule.dayNames[meeting.day - 1]} ${meeting.sessionsLabel} " +
                        "${BellSchedule.startOf(meeting.firstSession)}-${BellSchedule.endOf(meeting.lastSession)}",
                )
                DetailRow("地点", meeting.location)
                DetailRow("周次", meeting.weekDescription)
                DetailRow("课程号", course.code)
                DetailRow(
                    "性质",
                    listOf(course.property, course.category).filter { it.isNotBlank() }.joinToString(" · "),
                )
                DetailRow("学分", course.credits.toString())
                DetailRow("考核", course.examType)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(value, fontSize = 13.sp)
    }
}

@Composable
private fun UnscheduledSection(schedule: Schedule) {
    val items = schedule.courses.filter { it.meetings.isEmpty() }
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "未安排上课时间 · ${items.size} 门" + if (open) " ▴" else " ▾",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open) {
            items.forEach {
                Text(
                    text = "· ${it.name}　${it.teacher}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun courseColor(course: Course): Color =
    CourseColors[course.code.hashCode().mod(CourseColors.size)]
