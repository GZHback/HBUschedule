package cn.hbu.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.data.AppPrefs
import cn.hbu.schedule.data.Calibration
import cn.hbu.schedule.data.ManualStore
import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.data.SchoolWeekHint
import cn.hbu.schedule.ui.LoginScreen
import cn.hbu.schedule.ui.ScheduleScreen
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

            Surface(color = MaterialTheme.colorScheme.background) {
                val cached = rawSchedule
                if (cached != null) {
                    val schedule = remember(cached, manualEntries) {
                        ManualStore.merged(ScheduleParser.parse(cached), manualEntries)
                    }
                    val calibration = remember(hint, settings) {
                        Calibration.between(hint, settings, LocalDate.now())
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "本地缓存课表，可能不是最新",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { rawSchedule = null }) {
                                Text("重新登录刷新", fontSize = 11.sp)
                            }
                        }
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
                        )
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
