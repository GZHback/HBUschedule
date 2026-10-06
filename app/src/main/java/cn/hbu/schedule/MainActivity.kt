// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cn.hbu.schedule.data.AppPrefs
import cn.hbu.schedule.data.Calibration
import cn.hbu.schedule.data.ManualStore
import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.data.SchoolWeekHint
import cn.hbu.schedule.ui.Ios
import cn.hbu.schedule.ui.LocalCourseColors
import cn.hbu.schedule.ui.LoginScreen
import cn.hbu.schedule.ui.ScheduleScreen
import cn.hbu.schedule.ui.courseColorMap
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember(context) { AppPrefs(context) }

            // 抓来的原始 JSON、手动排的课、学期起点、上次读到的教务周次，四样各自存本机
            var rawSchedule by remember { mutableStateOf(prefs.readCache()) }
            var settings by remember { mutableStateOf(prefs.termSettings()) }
            var manualEntries by remember { mutableStateOf(prefs.manualEntries()) }
            var hint by remember { mutableStateOf(prefs.schoolWeekHint()) }

            Surface(color = Ios.Page) {
                val cached = rawSchedule
                if (cached != null) {
                    val schedule = remember(cached, manualEntries) {
                        ManualStore.merged(ScheduleParser.parse(cached), manualEntries)
                    }
                    val calibration = remember(hint, settings) {
                        Calibration.between(hint, settings, LocalDate.now())
                    }
                    // 一门课一个色：按整张课表统一配色，色板够用时门门不同
                    val courseColors = remember(schedule) {
                        courseColorMap(schedule.courses.map { it.code })
                    }
                    // 状态栏高度只在这里扣一次：之前这里和 ScheduleScreen 各扣一遍，顶部多出一条空白
                    CompositionLocalProvider(LocalCourseColors provides courseColors) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                        ) {
                            ScheduleScreen(
                                schedule = schedule,
                                term = settings.toTerm(),
                                settings = settings,
                                manualEntries = manualEntries,
                                calibration = calibration,
                                onSaveManual = { entry ->
                                    // 同一个 id 就是改那条，新 id 才是加一条
                                    manualEntries = if (manualEntries.any { it.id == entry.id }) {
                                        manualEntries.map { if (it.id == entry.id) entry else it }
                                    } else {
                                        manualEntries + entry
                                    }
                                    prefs.saveManualEntries(manualEntries)
                                },
                                onDeleteManual = { id ->
                                    manualEntries = manualEntries.filterNot { it.id == id }
                                    prefs.saveManualEntries(manualEntries)
                                },
                                onSaveTerm = { newSettings ->
                                    settings = newSettings
                                    hint = null
                                    prefs.saveTermSettings(newSettings)
                                    prefs.clearSchoolWeekHint()
                                },
                                onRelogin = { rawSchedule = null },
                            )
                        }
                    }
                } else {
                    // 登录 + 抓取都在 WebView 内完成，直接拿到 callback 原始 JSON
                    LoginScreen { raw, schoolWeek ->
                        prefs.writeCache(raw)
                        if (schoolWeek != null) {
                            val fresh = SchoolWeekHint(schoolWeek, LocalDate.now())
                            hint = fresh
                            prefs.saveSchoolWeekHint(fresh)
                        }
                        rawSchedule = raw
                    }
                }
            }
        }
    }
}
