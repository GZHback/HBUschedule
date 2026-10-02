package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
private val SlotHeight = 65.dp //单元格格高
private val BreakHeight = 24.dp
private val TimeWidth = 35.dp
private val HeaderHeight = 22.dp

private val CourseColors = listOf(
    Color(0xFFDC2626), // 红
    Color(0xFFEA580C), // 橙
    Color(0xFFB45309), // 琥珀
    Color(0xFF059669), // 翠绿
    Color(0xFF0D9488), // 青
    Color(0xFF0284C7), // 天蓝
    Color(0xFF2563EB), // 蓝
    Color(0xFF4F46E5), // 靛蓝
    Color(0xFF9333EA), // 紫
    Color(0xFFDB2777), // 粉
)

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
) {
    val today = LocalDate.now()
    val thisWeek = term.weekOf(today)
    var week by remember(settings.week1Monday, settings.totalWeeks, thisWeek) {
        mutableIntStateOf(thisWeek ?: 1)
    }
    var selected by remember { mutableStateOf<Pair<Course, Meeting>?>(null) }
    var draft by remember { mutableStateOf<ManualDraft?>(null) }
    var termOpen by remember { mutableStateOf(false) }
    var unscheduledOpen by remember { mutableStateOf(true) }
    var manualOpen by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("河北大学", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "◀",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { week = (week - 1).coerceAtLeast(1) }.padding(10.dp),
                    )
                    Text(
                        text = "第 $week 周" + if (week == thisWeek) " · 本周" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 104.dp),
                    )
                    Text(
                        "▶",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { week = (week + 1).coerceAtMost(term.totalWeeks) }
                            .padding(10.dp),
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${monthDay(term.dateOf(1, week))} - ${monthDay(term.dateOf(7, week))}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { termOpen = true }) {
                        Text("学期设置", fontSize = 13.sp)
                    }
                    TextButton(onClick = { draft = ManualDraft(null, null) }) {
                        Text("＋ 加课", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (thisWeek == null) {
                    val offset = term.weekNumber(today)
                    NoticeBar(
                        text = "今天 ${monthDay(today)} 不在本学期第 1-${term.totalWeeks} 周里" +
                            (
                                if (offset < 1) "（第 1 周还没到）"
                                else "（按 ${monthDay(term.week1Monday)} 起算已经是第 $offset 周）"
                                ) +
                            "，格子先按第 1 周画 —— 点开「学期设置」核对",
                        onClick = { termOpen = true },
                    )
                }
                calibration?.let { info ->
                    NoticeBar(
                        text = "上次抓课表时，教务页面自己写着 ${monthDay(info.detectedOn)} 是第 ${info.schoolWeek} 周，" +
                            "我们算的是${if (info.ourWeek == null) "不在本学期里" else "第 ${info.ourWeek} 周"} —— 点进去可以按教务的改",
                        onClick = { termOpen = true },
                    )
                }

                // 表头：星期几固定在顶部
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Spacer(Modifier.width(TimeWidth))
                    (1..7).forEach { day ->
                        val isToday = day == today.dayOfWeek.value && week == thisWeek
                        Text(
                            text = BellSchedule.dayNames[day - 1],
                            fontSize = 12.sp,
                            maxLines = 1,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).height(HeaderHeight),
                        )
                    }
                }

                // 课表主体：时间轴固定在左侧，整体上下滚动；4/5 节之间午休、8/9 节之间晚休
                val onPick: (Course, Meeting) -> Unit = { course, meeting -> selected = course to meeting }
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
                        UnscheduledSection(
                            courses = unscheduled,
                            open = unscheduledOpen,
                            onToggle = { unscheduledOpen = !unscheduledOpen },
                            onSchedule = { draft = ManualDraft(it, null) },
                        )
                    }
                    if (manualEntries.isNotEmpty()) {
                        ManualSection(
                            entries = manualEntries,
                            open = manualOpen,
                            onToggle = { manualOpen = !manualOpen },
                            onEdit = { draft = ManualDraft(schedule.courses.firstOrNull { c -> c.code == it.courseCode }, it) },
                            onDelete = { onDeleteManual(it.id) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
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
            )
        }
    }

    selected?.let { (course, meeting) ->
        CourseDetail(
            course = course,
            meeting = meeting,
            manual = manualEntries.firstOrNull { it.id == meeting.manualId },
            onEdit = { entry ->
                selected = null
                draft = ManualDraft(schedule.courses.firstOrNull { c -> c.code == entry.courseCode }, entry)
            },
            onDelete = { id ->
                selected = null
                onDeleteManual(id)
            },
            onDismiss = { selected = null },
        )
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
    Column(modifier.padding(horizontal = 2.dp)) {
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
                        .height(SlotHeight * (bottom - top + 1) - 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(courseColor(course))
                        .clickable { onPick(course, meeting) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        course.name,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        course.teacher,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = Color(0xE6FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meeting.placeLabel.isNotBlank()) {
                        Text(
                            meeting.placeLabel,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = Color(0xCCFFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = BellSchedule.startOf(session),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = BellSchedule.endOf(session),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
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
                sessions = sessions,
                modifier = Modifier.weight(1f),
                onPick = onPick,
            )
        }
    }
}

/** 上午/下午、下午/晚上之间的休整分隔条。 */
@Composable
private fun BreakDivider(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(BreakHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 周次、学期对不上这类事，说在课表格子上面，别塞进设置页里 */
@Composable
private fun NoticeBar(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun CourseDetail(
    course: Course,
    meeting: Meeting,
    manual: ManualEntry?,
    onEdit: (ManualEntry) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
                if (course.credits > 0) DetailRow("学分", course.credits.toString())
                DetailRow("考核", course.examType)
                if (manual != null) {
                    DetailRow("来源", "手动排课，只存在这台手机上")
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (manual != null) {
                    TextButton(onClick = { onEdit(manual) }) { Text("改时间", fontSize = 14.sp) }
                    TextButton(onClick = { onDelete(manual.id) }) {
                        Text("删除", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("关闭", fontSize = 14.sp) }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(value, fontSize = 14.sp)
    }
}

/** 折叠区块的标题：名字 + 数量 + 展开箭头，点整行收起 */
@Composable
private fun SectionHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(if (open) "▴" else "▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 教务里只给了名字、没给上课时间的课（实验、课程设计这类）。
 * 之前是一行 12sp 的小灰字，看不清也不知道能干什么；现在是一门一张卡片，右侧直接是「排课」。
 */
@Composable
private fun UnscheduledSection(
    courses: List<Course>,
    open: Boolean,
    onToggle: () -> Unit,
    onSchedule: (Course) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SectionHeader("还没排上课时间的课", courses.size, open, onToggle)
        if (open) {
            FormHint(
                "教务系统里这几门只有名字，没有星期几第几节 —— 实验、课程设计、网课常是这样。" +
                    "点「排课」自己填上，填完它就会出现在上面那张表里。"
            )
            courses.forEach { course ->
                val meta = listOf(
                    course.teacher.takeIf { it.isNotBlank() }?.let { "老师 $it" },
                    course.credits.takeIf { it > 0 }?.let { "$it 学分" },
                    course.property.takeIf { it.isNotBlank() },
                ).filterNotNull().joinToString(" · ")
                InfoCard(
                    title = course.name,
                    subtitle = meta,
                    trailing = {
                        TextButton(onClick = { onSchedule(course) }) {
                            Text("排课", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
    }
}

/** 手动排的课集中在这儿，才有人去改和删；只藏在格子里是删不掉的 */
@Composable
private fun ManualSection(
    entries: List<ManualEntry>,
    open: Boolean,
    onToggle: () -> Unit,
    onEdit: (ManualEntry) -> Unit,
    onDelete: (ManualEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SectionHeader("我手动排的课", entries.size, open, onToggle)
        if (open) {
            entries.forEach { entry ->
                val subtitle = listOf(
                    BellSchedule.dayNames[entry.day - 1],
                    entry.sessionLabel,
                    entry.weekLabel,
                    entry.placeLabel,
                ).filter { it.isNotBlank() }.joinToString(" ")
                InfoCard(
                    title = entry.courseName,
                    subtitle = subtitle,
                    trailing = {
                        Row {
                            TextButton(onClick = { onEdit(entry) }) { Text("改", fontSize = 14.sp) }
                            TextButton(onClick = { onDelete(entry) }) {
                                Text("删", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun courseColor(course: Course): Color =
    CourseColors[course.code.hashCode().mod(CourseColors.size)]

private fun monthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
