package cn.hbu.schedule.data

import android.content.Context
import cn.hbu.schedule.model.ManualEntry
import java.io.File

/**
 * 全都在学生自己手机上：课表快照、手动排的课、学期起点。没有任何一项上传。
 *
 * 文件名和键名保持和之前一致，老版本装过的人升级后缓存课表不会凭空消失。
 */
class AppPrefs(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private val cacheFile = File(app.filesDir, CACHE_FILE)

    fun readCache(): String? = runCatching {
        if (cacheFile.exists()) cacheFile.readText() else null
    }.getOrNull()

    fun writeCache(raw: String) {
        runCatching { cacheFile.writeText(raw) }
    }

    fun termSettings(): TermSettings = TermSettings.decode(prefs.getString(KEY_TERM, null))

    fun saveTermSettings(settings: TermSettings) {
        prefs.edit().putString(KEY_TERM, TermSettings.encode(settings)).apply()
    }

    fun manualEntries(): List<ManualEntry> = ManualStore.decode(prefs.getString(KEY_MANUAL, null))

    fun saveManualEntries(entries: List<ManualEntry>) {
        prefs.edit().putString(KEY_MANUAL, ManualStore.encode(entries)).apply()
    }

    fun schoolWeekHint(): SchoolWeekHint? = SchoolWeekHint.decode(prefs.getString(KEY_SCHOOL_WEEK, null))

    fun saveSchoolWeekHint(hint: SchoolWeekHint) {
        prefs.edit().putString(KEY_SCHOOL_WEEK, SchoolWeekHint.encode(hint)).apply()
    }

    fun clearSchoolWeekHint() {
        prefs.edit().remove(KEY_SCHOOL_WEEK).apply()
    }

    private companion object {
        const val STORE = "hbu_schedule"
        const val CACHE_FILE = "schedule_cache.json"
        const val KEY_TERM = "term_settings"
        const val KEY_MANUAL = "manual_entries"
        const val KEY_SCHOOL_WEEK = "school_week_hint"
    }
}
