// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * 周次和星期都从设备日期算，手动排的课并进来以后和教务课走同一条路径，
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
        if (thisWeek == null) emptyList()
        else TodayAgenda.items(schedule, today.dayOfWeek.value, thisWeek, nowMinutes)
    }
    val nextIndex = TodayAgenda.nextIndex(items)
    var explain by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(start = Ios.GapEdge, top = 10.dp, bottom = 6.dp)) {
            Text("今天", fontSize = Ios.LargeTitle, fontWeight = FontWeight.Bold, color = Ios.Label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${monthDay(today)} ${weekdayName(today)}" + if (thisWeek != null) " · 第 $thisWeek 周" else "",
                    fontSize = Ios.Subhead,
                    color = Ios.SecondaryLabel,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    Text(
                        "还有 ${TodayAgenda.remaining(items)} 节",
                        fontSize = Ios.Footnote,
                        color = Ios.SecondaryLabel,
                        modifier = Modifier.padding(end = Ios.GapEdge),
                    )
                }
            }
        }

        if (thisWeek == null) {
            val weeksIn = term.weekNumber(today)
            NoticeBar(
                text = "今天 ${monthDay(today)} 不在本学期第 1-${term.totalWeeks} 周里" +
                    (if (weeksIn < 1) "（第 1 周还没到）" else "（按 ${monthDay(term.week1Monday)} 起算已经是第 $weeksIn 周）"),
                onClick = onOpenTerm,
            )
        }

        if (items.isEmpty()) {
            GroupCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        if (thisWeek == null) "这一周不在学期里" else "今天没有排课",
                        fontSize = Ios.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = Ios.Label,
                    )
                    Text(
                        "要是今天其实有课，多半是学校调课或者学期起点没对上 —— 调课信息教务接口里没有，我们读不到。",
                        fontSize = Ios.Footnote,
                        lineHeight = 18.sp,
                        color = Ios.SecondaryLabel,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = onOpenWeek) {
                        Text("看整周课表", fontSize = Ios.Body, color = Ios.Tint)
                    }
                }
            }
        } else {
            GroupCard {
                items.forEachIndexed { index, item ->
                    TodayRow(item = item, next = index == nextIndex)
                    if (index != items.lastIndex) {
                        Hairline()
                    }
                }
            }
        }

        TextButton(
            onClick = { explain = !explain },
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        ) {
            Text(
                if (explain) "收起说明" else "这些「已结束 / 下一节」是怎么来的？",
                fontSize = Ios.Footnote,
                color = Ios.Tint,
            )
        }
        if (explain) {
            Footnote(
                "拿手机当前的时间跟作息表比：过了下课点算已结束，在起止之间算正在上，还没到的第一节标成下一节。" +
                    "这一页只在打开时算一次，不会自己走到下一节 —— 过了下课时间还挂着「正在上」，重开一下就好。" +
                    "至于今天到底上不上课：调课、节假日补课这些教务接口里没有，我们读不到，以学校通知为准。"
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun TodayRow(item: TodayAgenda.Item, next: Boolean) {
    val meeting = item.meeting
    val isNext = next && item.state == TodayAgenda.State.LATER
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isNext) Color(0x14007AFFL) else Color.Transparent)
            .padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(courseColor(item.course))
        )
        Column(Modifier.width(54.dp).padding(start = 10.dp)) {
            Text(
                BellSchedule.startOf(meeting.firstSession),
                fontSize = Ios.Subhead,
                fontWeight = FontWeight.Medium,
                color = Ios.Label,
                maxLines = 1,
            )
            Text(
                BellSchedule.endOf(meeting.lastSession),
                fontSize = Ios.Caption,
                color = Ios.TertiaryLabel,
                maxLines = 1,
            )
            Text(
                meeting.sessionsLabel,
                fontSize = Ios.Caption,
                color = Ios.TertiaryLabel,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                item.course.name,
                fontSize = Ios.Body,
                fontWeight = FontWeight.Medium,
                color = Ios.Label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.course.teacher, meeting.placeLabel).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = Ios.Footnote,
                lineHeight = 17.sp,
                color = Ios.SecondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isNext && meeting.weekDescription.isNotBlank()) {
                Text(
                    meeting.weekDescription,
                    fontSize = Ios.Caption,
                    color = Ios.TertiaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (isNext) "下一节" else TodayAgenda.label(item.state),
            fontSize = Ios.Footnote,
            maxLines = 1,
            color = if (item.state == TodayAgenda.State.PAST) Ios.TertiaryLabel else Ios.Tint,
        )
    }
}
