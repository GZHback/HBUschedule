package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.model.BellSchedule
import java.time.LocalDate

/**
 * 表单弹窗共用的几块：整屏表单骨架、左右调节一行、单选条、输入框。
 *
 * 整屏而不是AlertDialog：格子要放 7 个选项、又要调节次和周次，对话框那点高度只能把字压小，
 * 而「显示文字很小」正是要修的问题。
 */

@Composable
internal fun FormScaffold(
    title: String,
    confirmLabel: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("返回", fontSize = 14.sp) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("取消", fontSize = 14.sp) }
                TextButton(onClick = onSave) {
                    Text(
                        confirmLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** 「节次 第 5 节 14:30-15:15」这种一行两向调节，沿用课表表头的 ◀ ▶ */
@Composable
internal fun StepperRow(
    label: String,
    value: String,
    hint: String = "",
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f))
        Text(
            "◀",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onMinus).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(
            "▶",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPlus).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    if (hint.isNotBlank()) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 一排互斥选项：星期、周次步长都用它，不用 FlowRow（那是实验式 API） */
@Composable
internal fun ChipRow(options: List<String>, selected: Set<Int>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        options.forEachIndexed { index, label ->
            val on = index in selected
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = if (options.size > 7) 11.sp else 13.sp,
                    maxLines = 1,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun FormHint(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
internal fun FormSectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
    )
}

/** 中文日期短写，界面上到处要用，别每个文件抄一遍 */
internal fun monthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/** 周一 .. 周日，和 BellSchedule.dayNames 的编号一致（DayOfWeek 周一就是 1） */
internal fun weekdayName(date: LocalDate): String = BellSchedule.dayNames[date.dayOfWeek.value - 1]
