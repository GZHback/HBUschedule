package cn.hbu.schedule.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import cn.hbu.schedule.data.Calibration
import cn.hbu.schedule.data.TermSettings
import cn.hbu.schedule.model.BellSchedule
import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.ManualEntry
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule
import cn.hbu.schedule.model.Term
import java.time.LocalDate

private const val SESSION_COUNT = 11
private const val TAB_TODAY = 0
private const val TAB_WEEK = 1

/** 一格多高、时间轴多宽：整屏都按这两个数排 */
private val SlotHeight = 65.dp
private val TimeWidth = 38.dp
private val HeaderHeight = 38.dp

/** 打开排课表单时带上的是谁：course 为空表示完全自建，entry 非空表示在改一条已有的 */
private data class ManualDraft(val course: Course?, val entry: ManualEntry?)

@Composable
fun ScheduleScreen(
    schedule: Schedule,
    term: Term,
    settings: TermSettings,
    manualEntries: List<ManualEntry>,
    calibration: Calibration?,
    onSaveManual: (ManualEntry) -> Unit,
    onDeleteManual: (String) -> Unit,
    onSaveTerm: (TermSettings) -> Unit,
    onRelogin: () -> Unit,
) {
    val today = LocalDate.now()
    val thisWeek = term.weekOf(today)
    var tab by remember { mutableIntStateOf(TAB_TODAY) }
    var week by remember(settings.week1Monday, settings.totalWeeks, thisWeek) {
        mutableIntStateOf(thisWeek ?: 1)
    }
    var selected by remember { mutableStateOf<Pair<Course, Meeting>?>(null) }
    var draft by remember { mutableStateOf<ManualDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<ManualEntry?>(null) }
    var termOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Ios.Page)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Ios.GapEdge, end = 4.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedTabs(listOf("今天", "课表"), selected = tab, onSelect = { tab = it })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { termOpen = true }) {
                    Text("学期设置", fontSize = Ios.Subhead, color = Ios.Tint)
                }
                TextButton(onClick = { draft = ManualDraft(null, null) }) {
                    Text("＋ 加课", fontSize = Ios.Subhead, fontWeight = FontWeight.Medium, color = Ios.Tint)
                }
            }

            Crossfade(targetState = tab, modifier = Modifier.weight(1f), label = "页面切换") { which ->
                if (which == TAB_TODAY) {
                    TodayColumn(
                        schedule = schedule,
                        term = term,
                        today = today,
                        thisWeek = thisWeek,
                        onOpenTerm = { termOpen = true },
                        onOpenWeek = { tab = TAB_WEEK },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    WeekPage(
                        schedule = schedule,
                        term = term,
                        week = week,
                        today = today,
                        thisWeek = thisWeek,
                        manualEntries = manualEntries,
                        calibration = calibration,
                        onWeekChange = { week = it },
                        onPick = { course, meeting -> selected = course to meeting },
                        onOpenTerm = { termOpen = true },
                        onSchedule = { course -> draft = ManualDraft(course, null) },
                        onEditManual = { entry ->
                            draft = ManualDraft(schedule.courses.firstOrNull { c -> c.code == entry.courseCode }, entry)
                        },
                        onDeleteManual = { entry -> pendingDelete = entry },
                    )
                }
            }
        }

        draft?.let { current ->
            ManualCourseDialog(
                course = current.course,
                preset = current.entry,
                onSave = { entry ->
                    onSaveManual(entry)
                    draft = null
                },
                onDismiss = { draft = null },
            )
        }

        if (termOpen) {
            TermSettingsDialog(
                settings = settings,
                calibration = calibration,
                onSave = { onSaveTerm(it) },
                onDismiss = { termOpen = false },
                onRelogin = onRelogin,
            )
        }

        pendingDelete?.let { entry ->
            DeleteConfirm(
                entry = entry,
                onCancel = { pendingDelete = null },
                onConfirm = {
                    onDeleteManual(entry.id)
                    pendingDelete = null
                },
            )
        }
    }

    selected?.let { (course, meeting) ->
        val manual = manualEntries.firstOrNull { it.id == meeting.manualId }
        CourseDetail(
            course = course,
            meeting = meeting,
            manual = manual,
            onEdit = { entry ->
                selected = null
                draft = ManualDraft(schedule.courses.firstOrNull { c -> c.code == entry.courseCode }, entry)
            },
            onDelete = {
                selected = null
                manual?.let { pendingDelete = it }
            },
            onDismiss = { selected = null },
        )
    }
}

/** 周课表那一页：周导航 + 星期表头 + 三段格子 + 下面两个列表 */
@Composable
private fun WeekPage(
    schedule: Schedule,
    term: Term,
    week: Int,
    today: LocalDate,
    thisWeek: Int?,
    manualEntries: List<ManualEntry>,
    calibration: Calibration?,
    onWeekChange: (Int) -> Unit,
    onPick: (Course, Meeting) -> Unit,
    onOpenTerm: () -> Unit,
    onSchedule: (Course) -> Unit,
    onEditManual: (ManualEntry) -> Unit,
    onDeleteManual: (ManualEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "◀",
                fontSize = Ios.Body,
                color = Ios.Tint,
                modifier = Modifier.clickable { onWeekChange((week - 1).coerceAtLeast(1)) }.padding(10.dp),
            )
            Text(
                text = "第 $week 周" + if (week == thisWeek) " · 本周" else "",
                fontSize = Ios.Title,
                fontWeight = FontWeight.SemiBold,
                color = Ios.Label,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 118.dp),
            )
            Text(
                "▶",
                fontSize = Ios.Body,
                color = Ios.Tint,
                modifier = Modifier
                    .clickable { onWeekChange((week + 1).coerceAtMost(term.totalWeeks)) }
                    .padding(10.dp),
            )
        }
        if (thisWeek == null) {
            val weeksIn = term.weekNumber(today)
            NoticeBar(
                text = "今天 ${monthDay(today)} 不在本学期第 1-${term.totalWeeks} 周里" +
                    (
                        if (weeksIn < 1) "（第 1 周还没到）"
                        else "（按 ${monthDay(term.week1Monday)} 起算已经是第 $weeksIn 周）"
                        ) +
                    "，格子先按第 1 周画",
                onClick = onOpenTerm,
            )
        }
        calibration?.let { info ->
            NoticeBar(
                text = "上次抓课表时，教务页面自己写着 ${monthDay(info.detectedOn)} 是第 ${info.schoolWeek} 周，" +
                    "我们算的是${if (info.ourWeek == null) "不在本学期里" else "第 ${info.ourWeek} 周"}",
                onClick = onOpenTerm,
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            Spacer(Modifier.width(TimeWidth))
            (1..7).forEach { day ->
                val isToday = day == today.dayOfWeek.value && week == thisWeek
                val date = term.dateOf(day, week)
                Column(
                    modifier = Modifier.weight(1f).height(HeaderHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = BellSchedule.dayNames[day - 1],
                        fontSize = Ios.Footnote,
                        maxLines = 1,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) Ios.Tint else Ios.SecondaryLabel,
                    )
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}",
                        fontSize = Ios.Caption,
                        maxLines = 1,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) Ios.Tint else Ios.TertiaryLabel,
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
        ) {
            SessionRow(schedule, week, 1..4, onPick)
            BreakDivider("午休")
            SessionRow(schedule, week, 5..8, onPick)
            BreakDivider("晚休")
            SessionRow(schedule, week, 9..SESSION_COUNT, onPick)

            val unscheduled = schedule.courses.filter { it.meetings.isEmpty() }
            if (unscheduled.isNotEmpty()) {
                UnscheduledSection(courses = unscheduled, onSchedule = onSchedule)
            }
            if (manualEntries.isNotEmpty()) {
                ManualSection(entries = manualEntries, onEdit = onEditManual, onDelete = onDeleteManual)
            }
            Footnote("点一节看学分、考核和完整周次。")
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DayColumn(
    schedule: Schedule,
    day: Int,
    week: Int,
    sessions: IntRange,
    modifier: Modifier = Modifier,
    onPick: (Course, Meeting) -> Unit,
) {
    Column(modifier.padding(horizontal = 1.5.dp)) {
        Box(Modifier.fillMaxWidth().height(SlotHeight * sessions.count())) {
            schedule.meetingsOn(day, week).forEach { (course, meeting) ->
                // 一门课跨了午休/晚休时，两段各画自己那半，别让它在一边整块消失
                val top = maxOf(meeting.firstSession, sessions.first)
                val bottom = minOf(meeting.lastSession, sessions.last)
                if (bottom < top) return@forEach
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = SlotHeight * (top - sessions.first))
                        .height(SlotHeight * (bottom - top + 1) - 3.dp)
                        .clip(RoundedCornerShape(Ios.RadiusCell))
                        .background(courseColor(course))
                        .clickable { onPick(course, meeting) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        course.name,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.teacher.isNotBlank()) {
                        Text(
                            course.teacher,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = Color(0xD9FFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 地点分两行：先写楼（A*座），再写教室
                    if (bottom - top + 1 > 1) {
                        if (meeting.building.isNotBlank()) {
                            Text(
                                meeting.building,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = Color(0xD9FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (meeting.room.isNotBlank()) {
                            Text(
                                meeting.room,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = Color(0xD9FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 课表的一段：左侧时间轴 + 7 天格子，sessions 为该段包含的节次。 */
@Composable
private fun SessionRow(
    schedule: Schedule,
    week: Int,
    sessions: IntRange,
    onPick: (Course, Meeting) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(TimeWidth)) {
            sessions.forEach { session ->
                Column(
                    Modifier.fillMaxWidth().height(SlotHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = session.toString(),
                        fontSize = Ios.Body,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ios.Label,
                    )
                    Text(
                        text = BellSchedule.startOf(session),
                        fontSize = Ios.Caption,
                        lineHeight = 13.sp,
                        color = Ios.SecondaryLabel,
                    )
                    Text(
                        text = BellSchedule.endOf(session),
                        fontSize = Ios.Caption,
                        lineHeight = 13.sp,
                        color = Ios.TertiaryLabel,
                    )
                }
            }
        }
        (1..7).forEach { day ->
            DayColumn(
                schedule = schedule,
                day = day,
                week = week,
                sessions = sessions,
                modifier = Modifier.weight(1f),
                onPick = onPick,
            )
        }
    }
}

/** 上午/下午、下午/晚上之间：一根细线中间夹个小字，不再是一条灰杠 */
@Composable
private fun BreakDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(Ios.Hairline).background(Ios.Separator))
        Text(
            label,
            fontSize = Ios.Caption,
            color = Ios.TertiaryLabel,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(Modifier.weight(1f).height(Ios.Hairline).background(Ios.Separator))
    }
}

/** 周次、学期对不上这类事，说在课表格子上面，别塞进设置页里 */
@Composable
internal fun NoticeBar(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ios.GapEdge, vertical = 4.dp)
            .clip(RoundedCornerShape(Ios.RadiusCard))
            .background(Ios.Card)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ios.Destructive)
        )
        Text(
            text,
            fontSize = Ios.Footnote,
            lineHeight = 17.sp,
            color = Ios.Label,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text("›", fontSize = Ios.Body, color = Ios.TertiaryLabel)
    }
}

/** 分组列表的题头：小灰字，不带卡片 */
@Composable
private fun GroupHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = Ios.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.weight(1f))
        Text("$count", fontSize = Ios.Footnote, color = Ios.TertiaryLabel, modifier = Modifier.padding(end = 6.dp))
        Text(if (open) "▴" else "▾", fontSize = Ios.Footnote, color = Ios.TertiaryLabel)
    }
}

/** 一行：左边课程色条 + 标题/副标题，右边动作；行与行之间是内缩细线 */
@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    accent: Color,
    trailing: @Composable () -> Unit,
    last: Boolean,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    title,
                    fontSize = Ios.Body,
                    fontWeight = FontWeight.Medium,
                    color = Ios.Label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = Ios.Footnote,
                        lineHeight = 17.sp,
                        color = Ios.SecondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
        if (!last) {
            Hairline()
        }
    }
}

/**
 * 教务里只给了名字、没给上课时间的课（实验、课程设计这类）。
 * 点「排课」填上时间，它就会出现在上面那张表里。
 */
@Composable
private fun UnscheduledSection(courses: List<Course>, onSchedule: (Course) -> Unit) {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        GroupHeader("教务没排时间的课", courses.size, open) { open = !open }
        if (open) {
            GroupCard {
                courses.forEachIndexed { index, course ->
                    val meta = listOf(
                        course.teacher.takeIf { it.isNotBlank() }?.let { "老师 $it" },
                        course.credits.takeIf { it > 0 }?.let { "$it 学分" },
                        course.property.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · ")
                    ListRow(
                        title = course.name,
                        subtitle = meta,
                        accent = courseColor(course),
                        trailing = {
                            TextButton(onClick = { onSchedule(course) }) {
                                Text("排课", fontSize = Ios.Subhead, color = Ios.Tint)
                            }
                        },
                        last = index == courses.lastIndex,
                    )
                }
            }
            Footnote("实验、课程设计、网课常常只有名字，没有星期几第几节，得自己填。")
        }
    }
}

/** 手动排的课集中在这儿，才有人去改和删 */
@Composable
private fun ManualSection(
    entries: List<ManualEntry>,
    onEdit: (ManualEntry) -> Unit,
    onDelete: (ManualEntry) -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        GroupHeader("我手动排的课", entries.size, open) { open = !open }
        if (open) {
            GroupCard {
                entries.forEachIndexed { index, entry ->
                    val subtitle = listOf(
                        BellSchedule.dayNames[entry.day - 1],
                        entry.sessionLabel,
                        entry.weekLabel,
                        entry.placeLabel,
                    ).filter { it.isNotBlank() }.joinToString(" ")
                    ListRow(
                        title = entry.courseName,
                        subtitle = subtitle,
                        accent = courseColorOf(entry.scheduleCode),
                        trailing = {
                            Row {
                                TextButton(onClick = { onEdit(entry) }) {
                                    Text("改", fontSize = Ios.Subhead, color = Ios.Tint)
                                }
                                TextButton(onClick = { onDelete(entry) }) {
                                    Text("删", fontSize = Ios.Subhead, color = Ios.Destructive)
                                }
                            }
                        },
                        last = index == entries.lastIndex,
                    )
                }
            }
            Footnote("这些只存在这台手机上，重新抓课表不会动它们。")
        }
    }
}

/** 删了不会自己回来（重新抓课表也找不回这条），所以必须问一句 */
@Composable
private fun DeleteConfirm(entry: ManualEntry, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ios.Card,
        title = {
            Text("删除这条手动排的课？", fontSize = Ios.Body, fontWeight = FontWeight.SemiBold, color = Ios.Label)
        },
        text = {
            Text(
                "「${entry.courseName}」 ${BellSchedule.dayNames[entry.day - 1]} ${entry.sessionLabel} ${entry.weekLabel}。" +
                    "只删这台手机上的一条记录，教务里那门课不动。",
                fontSize = Ios.Subhead,
                lineHeight = 20.sp,
                color = Ios.SecondaryLabel,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", fontSize = Ios.Body, fontWeight = FontWeight.SemiBold, color = Ios.Destructive)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消", fontSize = Ios.Body, color = Ios.Tint) }
        },
    )
}

@Composable
private fun CourseDetail(
    course: Course,
    meeting: Meeting,
    manual: ManualEntry?,
    onEdit: (ManualEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ios.Card,
        title = { Text(course.name, fontSize = Ios.Title, fontWeight = FontWeight.SemiBold, color = Ios.Label) },
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
                if (course.credits > 0) DetailRow("学分", course.credits.toString())
                DetailRow("考核", course.examType)
                if (manual != null) DetailRow("来源", "手动排课，只存在这台手机上")
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (manual != null) {
                    TextButton(onClick = { onEdit(manual) }) { Text("改时间", fontSize = Ios.Body, color = Ios.Tint) }
                    TextButton(onClick = onDelete) { Text("删除", fontSize = Ios.Body, color = Ios.Destructive) }
                }
                TextButton(onClick = onDismiss) { Text("关闭", fontSize = Ios.Body, color = Ios.Tint) }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = Ios.Footnote, color = Ios.SecondaryLabel, modifier = Modifier.width(54.dp))
        Text(value, fontSize = Ios.Subhead, color = Ios.Label)
    }
}
