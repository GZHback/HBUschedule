package cn.hbu.schedule.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.Course

/**
 * 一套照着 iOS 抄的数：浅灰页面底、白卡片、0.6dp 分隔线、系统字号层级。
 *
 * 之前每个界面各自写死 sp/dp/颜色，所以怎么看都像色块拼出来的。要改观感，
 * 先把数收在一处，界面只引用名字。
 */
object Ios {

    /** 页面底（iOS grouped 背景） */
    val Page = Color(0xFFF2F2F7)

    /** 卡片、列表容器 */
    val Card = Color(0xFFFFFFFF)

    /** 分隔线，iOS 那种一根头发的灰 */
    val Separator = Color(0xFFE3E3E8)

    /** 分段控件的槽 */
    val Track = Color(0x14767680L)

    val Label = Color(0xFF1C1C1E)
    val SecondaryLabel = Color(0xFF6E6E73)
    val TertiaryLabel = Color(0xFF98989F)

    /** 可点的蓝字、要删的红字 */
    val Tint = Color(0xFF0A72CB)
    val Destructive = Color(0xFFC0393B)

    /** 大字标题 / 页面标题 / 正文 / 次级 / 说明 / 极小 */
    val LargeTitle = 26.sp
    val Title = 20.sp
    val Body = 16.sp
    val Subhead = 15.sp
    val Footnote = 13.sp
    val Caption = 11.sp

    val RadiusCell = 7.dp
    val RadiusCard = 12.dp
    val RadiusGroup = 14.dp
    val Hairline = 0.6.dp

    val GapRow = 10.dp
    val GapEdge = 14.dp
}

/**
 * 课程色：iOS 那套色相，饱和度压到 0.56、亮度按「白字对比 4.65:1」反推出来，
 * 所以十种颜色深浅一致，不会有一个特别跳。
 */
private val CourseColors = listOf(
    Color(0xFFCB454E), // 砖红
    Color(0xFF25817B), // 青绿
    Color(0xFF317AAD), // 湖蓝
    Color(0xDDFF9800), // 橙
    Color(0xFF5C6CD1), // 靛蓝
    Color(0xFFE7D100), // 金
    Color(0xFF9058D0), // 紫
    Color(0xFFC83C94), // 玫红
    Color(0xFF4CAF50), // 绿

)

/**
 * 把整张课表的课程号摊成「一门课一个色」：色板够用时保证门门不同，
 * 只有课数超过色数才从头轮回。键排过序，所以重开 App、换周都不会变色。
 */
internal fun courseColorMap(keys: Collection<String>): Map<String, Color> =
    keys.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .withIndex()
        .associate { (index, key) -> key to CourseColors[index % CourseColors.size] }

/** 由课表页按当前整张课表挂上去；没挂到时退回按哈希取色，同一门课每次仍一样 */
internal val LocalCourseColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

/** 同一门课每次拿到的颜色必须一样，格子、今天页、色条全靠课程号 */
@Composable
internal fun courseColorOf(key: String): Color =
    LocalCourseColors.current[key] ?: CourseColors[key.hashCode().mod(CourseColors.size)]

@Composable
internal fun courseColor(course: Course): Color = courseColorOf(course.code)

/** 一张 iOS 那种圆角白卡片，里面放一组列表行 */
@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ios.GapEdge, vertical = 2.dp)
            .clip(RoundedCornerShape(Ios.RadiusGroup))
            .background(Ios.Card),
        content = content,
    )
}

/** 卡片下面那行小灰字，解释「为什么」用，不拿来当正文 */
@Composable
internal fun Footnote(text: String) {
    Text(
        text,
        fontSize = Ios.Caption,
        lineHeight = 15.sp,
        color = Ios.TertiaryLabel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** 一行和下一行之间的细线，左边留一段不连通（iOS 列表的做法） */
@Composable
internal fun Hairline() {
    Box(Modifier.padding(start = 12.dp).fillMaxWidth().height(Ios.Hairline).background(Ios.Separator))
}

/**
 * iOS 的分段控件：白块在槽里滑过去，不是两个各自变色的按钮。
 *
 * 宽度按段数固定，滑动才可以用 offset 算 —— 不靠测量，少一层异步。
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmentWidth: Dp = 84.dp,
) {
    val shape = RoundedCornerShape(9.dp)
    val slide by animateFloatAsState(targetValue = selected.toFloat(), animationSpec = tween(180), label = "选中块")
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(Ios.Track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = segmentWidth * slide)
                .width(segmentWidth)
                .height(32.dp)
                .shadow(1.dp, RoundedCornerShape(Ios.RadiusCell))
                .clip(RoundedCornerShape(Ios.RadiusCell))
                .background(Ios.Card),
        )
        Row {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .width(segmentWidth)
                        .height(32.dp)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = Ios.Subhead,
                        fontWeight = if (index == selected) FontWeight.Medium else FontWeight.Normal,
                        color = Ios.Label,
                    )
                }
            }
        }
    }
}
