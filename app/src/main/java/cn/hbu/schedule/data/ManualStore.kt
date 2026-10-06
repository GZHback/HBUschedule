// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 咕杼 及 HBUschedule 项目其他贡献者
// 本文件遵循 GPL-3.0-or-later 发布，条款见仓库根目录 LICENSE（本程序无担保）。
package cn.hbu.schedule.data

import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.ManualEntry
import cn.hbu.schedule.model.Schedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 手动排课本机存储：编解码是纯字符串进出，方便单元测试；
 * 谁把它写进 SharedPreferences 由 [AppPrefs] 负责。
 */
object ManualStore {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entries: List<ManualEntry>): String =
        buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("id", entry.id)
                        put("courseCode", entry.courseCode)
                        put("courseName", entry.courseName)
                        put("teacher", entry.teacher)
                        put("day", entry.day.toString())
                        put("firstSession", entry.firstSession.toString())
                        put("lastSession", entry.lastSession.toString())
                        put("weeks", buildJsonArray { entry.weeks.forEach { add(JsonPrimitive(it.toString())) } })
                        put("building", entry.building)
                        put("room", entry.room)
                    }
                )
            }
        }.toString()

    /** 存的东西读坏了不能连累课表打不开，任何异常都当没有手动记录 */
    fun decode(text: String?): List<ManualEntry> = try {
        if (text.isNullOrBlank()) emptyList() else json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            runCatching { element.toEntry() }.getOrNull()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun JsonElement.toEntry(): ManualEntry {
        val obj = jsonObject
        return ManualEntry(
            id = obj.string("id").orEmpty(),
            courseCode = obj.string("courseCode").orEmpty().trim(),
            courseName = obj.string("courseName").orEmpty().trim(),
            teacher = obj.string("teacher").orEmpty().trim(),
            day = obj.integer("day") ?: 1,
            firstSession = obj.integer("firstSession") ?: 1,
            lastSession = obj.integer("lastSession") ?: 1,
            weeks = obj["weeks"]?.takeIf { it !is JsonNull }?.jsonArray.orEmpty().mapNotNull {
                it.jsonPrimitive.content.trim().toIntOrNull()
            },
            building = obj.string("building").orEmpty().trim(),
            room = obj.string("room").orEmpty().trim(),
        )
    }

    /**
     * 把手动记录并到抓来的课表上，界面、详情、`.ics` 导出都因此一并看到它们。
     *
     * 挂在教务课程号上的并进那门课；对不上号的按课程名归成自建课，属性写「手动添加」，
     * 这样未排课那一栏会自动少一门，不会出现同一门课两处可见。
     */
    fun merged(schedule: Schedule, entries: List<ManualEntry>): Schedule {
        if (entries.isEmpty()) return schedule
        val byKey = LinkedHashMap<String, MutableList<ManualEntry>>()
        entries.forEach { group -> byKey.getOrPut(groupKey(group)) { ArrayList() }.add(group) }

        val courses = schedule.courses.toMutableList()
        val matched = HashSet<String>()
        courses.forEachIndexed { index, course ->
            val extra = byKey[course.code].orEmpty()
            if (extra.isNotEmpty()) {
                courses[index] = course.copy(meetings = course.meetings + extra.map { it.toMeeting() })
                matched += course.code
            }
        }

        byKey.filterKeys { it !in matched }.forEach { (_, group) ->
            val first = group.first()
            courses += Course(
                code = first.scheduleCode,
                name = first.courseName.ifBlank { "未命名课程" },
                teacher = first.teacher,
                credits = 0.0,
                examType = "",
                category = "",
                property = "手动添加",
                meetings = group.map { it.toMeeting() },
            )
        }
        return Schedule(courses)
    }

    /**
     * 归组用的键：教务课程号优先，自建课没号就按名字归到同一门
     *（同名分两次加的课是一门课的两个时间，不是两门课）。前缀是保险，真实课程号不长这样。
     */
    private fun groupKey(entry: ManualEntry): String =
        if (entry.isStandalone) STANDALONE_PREFIX + entry.courseName.trim() else entry.courseCode

    private const val STANDALONE_PREFIX = "自建:"

    private fun JsonObject.string(key: String): String? {
        val element: JsonElement = this[key] ?: return null
        return if (element is JsonNull) null else element.jsonPrimitive.content
    }

    private fun JsonObject.integer(key: String): Int? = string(key)?.trim()?.toIntOrNull()
}
