package cn.hbu.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.model.Term
import cn.hbu.schedule.ui.ScheduleScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = assets.open("sample_raw_schedule.json").bufferedReader().use { it.readText() }
        val schedule = ScheduleParser.parse(raw)
        // 学期起点先写死，登录与学期设置界面在下一版接上
        setContent { ScheduleScreen(schedule, Term(LocalDate.of(2026, 8, 31), 20)) }
    }
}
