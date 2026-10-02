package cn.hbu.schedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cn.hbu.schedule.data.Calibration
import cn.hbu.schedule.data.TermSettings
import cn.hbu.schedule.model.Term
import java.time.LocalDate

/**
 * 学期起点设置。第 1 周周一是唯一决定「今天第几周」的数，所以这里必须当场把换算结果演给学生看，
 * 改一下就能看到「今天 → 第 N 周」，不用猜。
 *
 * [calibration] 是教务页面自己标的周次和现有设置对不上时带进来的，按它一下就改好。
 */
@Composable
fun TermSettingsDialog(
    settings: TermSettings,
    calibration: Calibration?,
    onSave: (TermSettings) -> Unit,
    onDismiss: () -> Unit,
    onRelogin: () -> Unit,
) {
    var monday by remember { mutableStateOf(settings.week1Monday) }
    var weeks by remember { mutableIntStateOf(settings.totalWeeks) }
    val term = remember(monday, weeks) { Term(monday, weeks) }
    val today = LocalDate.now()
    val thisWeek = term.weekOf(today)

    FormScaffold(
        title = "学期设置",
        confirmLabel = "保存",
        canSave = true,
        onSave = { onSave(TermSettings(monday, weeks)) },
        onCancel = onDismiss,
    ) {
        FormHint(
            "课表上的周次不是我数出来的，是拿今天的日期减去「第 1 周的周一」算出来的，" +
                "所以它不会自己多一周或少一周。会出错的只有这个起点：学校真正开学那周和我写的日期差一天，" +
                "整学期就都差一周。"
        )

        calibration?.let { info ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    "上次抓课表时，教务页面上写着 ${monthDay(info.detectedOn)} 是第 ${info.schoolWeek} 周，" +
                        "而我们按现在的设置算出的是${info.ourWeek?.let { "第 $it 周" } ?: "不在本学期里"}。",
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = Ios.Destructive,
                )
                TextButton(onClick = { monday = info.week1Monday }) {
                    Text("按教务的改成 ${monthDay(info.week1Monday)}", fontSize = 14.sp)
                }
            }
        }

        FormSectionTitle("第 1 周的周一")
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            TextButton(onClick = { monday = monday.minusWeeks(1) }) { Text("◀ 一周", fontSize = 14.sp) }
            Text(
                "${monday.year}年${monday.monthValue}月${monday.dayOfMonth}日 · ${weekdayName(monday)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { monday = monday.plusWeeks(1) }) { Text("一周 ▶", fontSize = 14.sp) }
        }
        FormHint(
            "教务课表页上「第 1 周」那周的星期一是哪天，这里就填哪天。只按整周调 —— " +
                "起点得是周一，差一天整学期星期就错位了。"
        )

        StepperRow(
            "本学期一共",
            "$weeks 周",
            hint = "第 $weeks 周：${rangeLabel(term, weeks)}",
            onMinus = { weeks = (weeks - 1).coerceAtLeast(1) },
            onPlus = { weeks = (weeks + 1).coerceAtMost(30) },
        )

        FormSectionTitle("换算结果")
        Text(
            "第 1 周：${rangeLabel(term, 1)}\n" +
                "第 ${weeks} 周：${rangeLabel(term, weeks)}",
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Text(
            if (thisWeek == null) {
                "今天 ${monthDay(today)} · ${weekdayName(today)}：不在第 1-$weeks 周里（课表会提示这一点，格子按第 1 周显示）"
            } else {
                "今天 ${monthDay(today)} · ${weekdayName(today)}：第 $thisWeek 周"
            },
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (thisWeek == null) Ios.Destructive else Ios.Tint,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        FormHint(
            "注意：周次对上了，不代表今天一定上课。国庆、放假补课、周六上周一的课这些调休，" +
                "教务接口里没有，我们读不到，只能你自己知道今天到底上不上。"
        )

        FormSectionTitle("课表来源")
        FormHint("现在这张课表是本地缓存的，可能不是最新。想拿教务最新的，就重新登录抓一次。")
        TextButton(
            onClick = onRelogin,
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Text("重新登录刷新", fontSize = 15.sp, color = Ios.Tint)
        }
    }
}

private fun rangeLabel(term: Term, week: Int): String {
    val start = term.dateOf(1, week)
    val end = term.dateOf(7, week)
    return "${monthDay(start)} - ${monthDay(end)}"
}
