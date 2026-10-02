package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「今天」页：只列今天要上的那几节，从上到下一眼读完。
 *
 * 周次和星期都是从设备日期算的，手动排的课并进来以后和教务课走同一条路径，
 * 所以这一页不需要额外知道任何事。
 */
@Composable
internal fun TodayColumn(
    schedule: Schedule,
    term: Term,
    today: LocalDate,
    thisWeek: Int?,
    onOpenTerm: () -> Unit,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMinutes = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val items = remember(schedule, today, thisWeek, nowMinutes) {
        if (thisWeek == null) emptyList() else TodayAgenda.items(schedule, today.dayOfWeek.value, thisWeek, nowMinutes)
    }
    val nextIndex = TodayAgenda.nextIndex(items)

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
    ) {
        if (thisWeek == null) {
            NoticeBar(
                text = "今天 ${monthDay(today)} 不在本学期第 1-${term.totalWeeks} 周里" +
                    "（按 ${monthDay(term.week1Monday)} 起算已经是第 ${term.weekNumber(today)} 周），" +
                    "先核对学期设置",
                onClick = onOpenTerm,
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${monthDay(today)} ${weekdayName(today)}" + if (thisWeek != null) " · 第 $thisWeek 周" else "",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                Text(
                    "还有 ${TodayAgenda.remaining(items)} 节",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 22.dp, horizontal = 14.dp),
            ) {
                Column {
                    Text(
                        if (thisWeek == null) "这一周不在学期里" else "今天没有排课",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "要是今天其实有课，多半是学校调课或者学期起点没对上 —— 调课信息教务接口里没有，我们读不到。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TextButton(onClick = onOpenWeek) { Text("看整周课表", fontSize = 14.sp) }
                }
            }
        }

        items.forEachIndexed { index, item ->
            TodayCard(item = item, next = index == nextIndex)
        }

        FormHint(
            "页面上「已结束 / 正在上 / 还没上」是拿手机当前时间跟作息表比的；" +
                "这一页只在打开时算一次，不会自己刷新 —— 过了下课时间还挂着「正在上」，重开一下就好。"
        )
    }
}

@Composable
private fun TodayCard(item: TodayAgenda.Item, next: Boolean) {
    val meeting = item.meeting
    val stateText = TodayAgenda.label(item.state)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (next) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左边一根课程色条，和格子里那块颜色对得上
        Box(
            Modifier
                .width(4.dp)
                .height(if (next) 58.dp else 52.dp)
                .background(if (next) MaterialTheme.colorScheme.primary else courseColor(item.course))
        )
        Column(
            Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                BellSchedule.startOf(meeting.firstSession),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                BellSchedule.endOf(meeting.lastSession),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                meeting.sessionsLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                item.course.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.course.teacher, meeting.placeLabel).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (next || item.state == TodayAgenda.State.NOW) {
                Text(
                    meeting.weekDescription,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (next && item.state == TodayAgenda.State.LATER) "下一节" else stateText,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            color = when {
                next && item.state == TodayAgenda.State.LATER -> MaterialTheme.colorScheme.primary
                item.state == TodayAgenda.State.NOW -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.widthIn(min = 46.dp).padding(end = 8.dp),
        )
    }
}
