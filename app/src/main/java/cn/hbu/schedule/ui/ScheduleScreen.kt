package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDate

@Composable
fun ScheduleScreen(schedule: Schedule, term: Term) {
    val today = LocalDate.now()
    val week = term.weekOf(today)
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "河北大学 · " + (week?.let { "第${it}周" } ?: "不在教学周内"),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                (1..7).forEach { day ->
                    DayColumn(schedule, day, week, day == today.dayOfWeek.value)
                }
            }
            val unscheduled = schedule.courses.filter { it.meetings.isEmpty() }
            if (unscheduled.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("未安排上课时间", style = MaterialTheme.typography.titleSmall)
                    unscheduled.forEach {
                        Text(
                            text = "· ${it.name}　${it.teacher}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(schedule: Schedule, day: Int, week: Int?, isToday: Boolean) {
    Column(Modifier.width(150.dp).padding(4.dp)) {
        Text(
            text = BellSchedule.dayNames[day - 1],
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        BellSchedule.sessions.forEach { session ->
            val cells = schedule.cellFor(day, session, week)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(vertical = 2.dp)
                    .background(
                        if (cells.isEmpty()) Color(0x0A000000) else MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(6.dp),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cells.filter { it.startsHere }.forEach { cell ->
                        Text(cell.course.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            cell.meeting.location,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
