package cn.hbu.schedule.data

import cn.hbu.schedule.model.Course
import cn.hbu.schedule.model.Meeting
import cn.hbu.schedule.model.Schedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ScheduleParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Schedule {
        val root = json.parseToJsonElement(text).jsonObject
        val courses = ArrayList<Course>()
        for (group in root["xkxx"]?.jsonArray.orEmpty()) {
            for ((code, value) in group.jsonObject) {
                val info = value.jsonObject
                val meetings = info["timeAndPlaceList"]?.jsonArray.orEmpty().map { it.jsonObject }.map { tp ->
                    val first = tp.integer("classSessions") ?: 1
                    Meeting(
                        day = tp.integer("classDay") ?: 1,
                        firstSession = first,
                        lastSession = first + (tp.integer("continuingSession") ?: 1) - 1,
                        weeks = weeksOfClassWeek(tp.string("classWeek")),
                        weekDescription = tp.string("weekDescription").orEmpty().trim(),
                        campus = tp.string("campusName").orEmpty().trim(),
                        building = tp.string("teachingBuildingName").orEmpty().trim(),
                        room = tp.string("classroomName").orEmpty().trim(),
                    )
                }
                courses += Course(
                    code = code,
                    name = info.string("courseName").orEmpty().trim(),
                    teacher = info.string("attendClassTeacher").orEmpty().trim(),
                    credits = info.string("unit")?.toDoubleOrNull() ?: 0.0,
                    meetings = meetings,
                )
            }
        }
        return Schedule(courses)
    }

    /**
     * classWeek 是 24 位 0/1 位图，第 i 位为 '1' 表示第 i+1 周有课，是周次的权威来源。
     * weekDescription 那串中文（"1-9周"、"2-16周双周"、"第7周"）只是给人看的，不要解析它。
     */
    fun weeksOfClassWeek(bitmap: String?): List<Int> =
        bitmap?.mapIndexedNotNull { index, flag -> if (flag == '1') index + 1 else null }.orEmpty()

    private fun JsonObject.string(key: String): String? {
        val element: JsonElement = this[key] ?: return null
        return if (element is JsonNull) null else element.jsonPrimitive.content
    }

    private fun JsonObject.integer(key: String): Int? = string(key)?.trim()?.toIntOrNull()
}
