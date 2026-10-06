// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * 排课表单和学期设置表单共用的几块。
 *
 * 表单是整屏而不是对话框：格子要放 7 个星期选项、又要调节次和周次，
 * 对话框那点高度只能把字压小，而「显示文字很小」正是要修的问题。
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
    Surface(Modifier.fillMaxSize(), color = Ios.Page) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("取消", fontSize = Ios.Body, color = Ios.Tint) }
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = Ios.Title, fontWeight = FontWeight.SemiBold, color = Ios.Label)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave) {
                    Text(
                        confirmLabel,
                        fontSize = Ios.Body,
                        fontWeight = if (canSave) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (canSave) Ios.Tint else Ios.TertiaryLabel,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Ios.GapEdge),
                content = content,
            )
        }
    }
}

/** 「第 5 节 14:30」这种一行两向调节 */
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
        Text(label, fontSize = Ios.Subhead, color = Ios.SecondaryLabel)
        Box(Modifier.weight(1f))
        Text(
            "◀",
            fontSize = Ios.Body,
            color = Ios.Tint,
            modifier = Modifier.clickable(onClick = onMinus).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            value,
            fontSize = Ios.Body,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = Ios.Label,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(
            "▶",
            fontSize = Ios.Body,
            color = Ios.Tint,
            modifier = Modifier.clickable(onClick = onPlus).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    if (hint.isNotBlank()) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            Text(hint, fontSize = Ios.Footnote, color = Ios.TertiaryLabel)
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
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Color(0x1F007AFFL) else Ios.Card)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = if (options.size > 7) Ios.Footnote else Ios.Subhead,
                    maxLines = 1,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) Ios.Tint else Ios.Label,
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
        Text(label, fontSize = Ios.Footnote, color = Ios.SecondaryLabel)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = Ios.Subhead, color = Ios.TertiaryLabel) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun FormHint(text: String) {
    Text(
        text,
        fontSize = Ios.Footnote,
        lineHeight = 18.sp,
        color = Ios.SecondaryLabel,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
internal fun FormSectionTitle(text: String) {
    Text(
        text,
        fontSize = Ios.Subhead,
        fontWeight = FontWeight.Medium,
        color = Ios.Label,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
    )
}

/** 中文日期短写，界面上到处要用，别每个文件抄一遍 */
internal fun monthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/** 周一 .. 周日，和 BellSchedule.dayNames 的编号一致（DayOfWeek 周一就是 1） */
internal fun weekdayName(date: LocalDate): String = BellSchedule.dayNames[date.dayOfWeek.value - 1]
