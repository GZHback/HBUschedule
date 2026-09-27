package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDate

private val SlotHeight = 46.dp
private val TimeColumnWidth = 50.dp
private val HeaderHeight = 20.dp

private val CourseColors = listOf(
    Color(0xFFDCCBEA), Color(0xFFC7E3F4), Color(0xFFCDEBD3), Color(0xFFF6E3B8),
    Color(0xFFF7CDBE), Color(0xFFE1DCF7), Color(0xFFBFE3E0), Color(0xFFF5D6E0),
)

@Composable
fun ScheduleScreen(schedule: Schedule, term: Term) {
    val today = LocalDate.now()
    val thisWeek = term.weekOf(today)
    var week by remember { mutableStateOf(thisWeek ?: 1) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("河北大学", style = MaterialTheme.typography.titleMedium)
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
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    "▶",
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { week = (week + 1).coerceAtMost(term.totalWeeks) }.padding(10.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Column(Modifier.width(TimeColumnWidth)) {
                    Spacer(Modifier.height(HeaderHeight))
                    BellSchedule.sessions.forEach { session ->
                        Column(Modifier.height(SlotHeight)) {
                            Text(
                                "第 $session 节",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                BellSchedule.startOf(session),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                (1..7).forEach { day ->
                    DayColumn(
                        schedule = schedule,
                        day = day,
                        week = week,
                        isToday = day == today.dayOfWeek.value && week == thisWeek,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            UnscheduledSection(schedule)
        }
    }
}

@Composable
private fun DayColumn(
    schedule: Schedule,
    day: Int,
    week: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 1.5.dp)) {
        Text(
            text = BellSchedule.dayNames[day - 1],
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(HeaderHeight),
        )
        Box(Modifier.fillMaxWidth().height(SlotHeight * BellSchedule.sessions.size)) {
            schedule.meetingsOn(day, week).forEach { (course, meeting) ->
                val span = meeting.lastSession - meeting.firstSession + 1
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = SlotHeight * (meeting.firstSession - 1))
                        .height(SlotHeight * span - 3.dp)
                        .background(courseColor(course), RoundedCornerShape(6.dp))
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                ) {
                    Text(
                        course.name,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (span > 1) {
                        Text(
                            meeting.placeLabel,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xB3000000),
                        )
                    }
                }
            }
        }
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
