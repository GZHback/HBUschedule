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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.model.Term
import cn.hbu.schedule.ui.LoginScreen
import cn.hbu.schedule.ui.ScheduleScreen
import java.io.File
import java.time.LocalDate

private const val CACHE_FILE = "schedule_cache.json"

// 学期起点先写死，后续从 /ajax/getSectionAndTime 元数据解析
private val TERM = Term(LocalDate.of(2026, 8, 31), 20)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var rawSchedule by remember { mutableStateOf(readCache()) }

            Surface(color = MaterialTheme.colorScheme.background) {
                val cached = rawSchedule
                if (cached != null) {
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
                        ScheduleScreen(ScheduleParser.parse(cached), TERM)
                    }
                } else {
                    // 登录 + 抓取都在 WebView 内完成，直接拿到 callback 原始 JSON
                    LoginScreen { raw ->
                        writeCache(raw)
                        rawSchedule = raw
                    }
                }
            }
        }
    }

    private fun readCache(): String? = try {
        val f = File(filesDir, CACHE_FILE)
        if (f.exists()) f.readText() else null
    } catch (_: Exception) {
        null
    }

    private fun writeCache(raw: String) {
        try {
            File(filesDir, CACHE_FILE).writeText(raw)
        } catch (_: Exception) {
        }
    }
}
