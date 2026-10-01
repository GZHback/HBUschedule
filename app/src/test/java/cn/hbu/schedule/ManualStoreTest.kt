package cn.hbu.schedule

import cn.hbu.schedule.data.ManualStore
import cn.hbu.schedule.data.ScheduleParser
import cn.hbu.schedule.model.ManualEntry
import cn.hbu.schedule.model.Term
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 手动排课的存储与合并：这些都能在 JVM 上跑，不需要 Android。 */
class ManualStoreTest {

    private fun entry(
        id: String = "m1",
        code: String = "",
        name: String,
        day: Int = 3,
        first: Int = 5,
        last: Int = 6,
        weeks: List<Int> = (3..14 step 2).toList(),
        building: String = "A3座",
        room: String = "510",
        teacher: String = "",
    ) = ManualEntry(
        id = id,
        courseCode = code,
        courseName = name,
        teacher = teacher,
        day = day,
        firstSession = first,
        lastSession = last,
        weeks = weeks,
        building = building,
        room = room,
    )

    @Test
    fun `存和读回来是同一批记录`() {
        val items = listOf(
            entry(id = "a", code = "1326I00003", name = "操作系统实验"),
            entry(id = "b", name = "雅思口语", day = 1, first = 9, last = 11, weeks = listOf(2, 4, 6), teacher = "王老师"),
        )
        val back = ManualStore.decode(ManualStore.encode(items))
        assertEquals(items, back)
    }

    @Test
    fun `存的字符串坏了就当没有手动记录`() {
        assertEquals(emptyList<ManualEntry>(), ManualStore.decode(null))
        assertEquals(emptyList<ManualEntry>(), ManualStore.decode(""))
        assertEquals(emptyList<ManualEntry>(), ManualStore.decode("{不是数组"))
        assertEquals(emptyList<ManualEntry>(), ManualStore.decode("[]坏"))
    }

    @Test
    fun `挂到教务课程号上的并进那门课，未排课少一门`() {
        val parsed = ScheduleParser.parse(sample())
        val before = parsed.courses.filter { it.meetings.isEmpty() }
        assertEquals(8, before.size)
        val lab = before.first { it.name == "操作系统实验" }

        val merged = ManualStore.merged(parsed, listOf(entry(code = lab.code, name = lab.name)))
        val after = merged.courses.first { it.code == lab.code }

        assertEquals(1, after.meetings.size)
        assertEquals(3, after.meetings.first().day)
        assertEquals(5, after.meetings.first().firstSession)
        assertEquals(6, after.meetings.first().lastSession)
        assertEquals("m1", after.meetings.first().manualId)
        assertEquals(7, merged.courses.count { it.meetings.isEmpty() })
        assertEquals(21, merged.courses.size)
    }

    @Test
    fun `自建课按名字归成一门，同名多条不重复出现`() {
        val parsed = ScheduleParser.parse(sample())
        val merged = ManualStore.merged(
            parsed,
            listOf(
                entry(id = "x", name = "雅思口语", day = 1),
                entry(id = "y", name = "雅思口语", day = 4, building = "线上"),
            ),
        )
        val added = merged.courses.filter { it.property == "手动添加" }
        assertEquals(1, added.size)
        assertEquals("雅思口语", added.first().name)
        assertEquals(2, added.first().meetings.size)
        assertEquals(22, merged.courses.size)
    }

    @Test
    fun `教务里查无此号的手动课也不会被丢掉`() {
        val merged = ManualStore.merged(ScheduleParser.parse(sample()), listOf(entry(code = "已退课999", name = "已退课的课")))
        val orphan = merged.courses.first { it.code == "已退课999" }
        assertEquals("已退课的课", orphan.name)
        assertEquals("m1", orphan.meetings.first().manualId)
    }

    @Test
    fun `周次文案按选出来的周次拼`() {
        // 3、5、7…是单周，止于 13
        assertEquals("第3-13周 单周", entry(weeks = (3..14 step 2).toList()).weekLabel)
        assertEquals("第2-8周 双周", entry(weeks = (2..8 step 2).toList()).weekLabel)
        assertEquals("第1-9周", entry(weeks = (1..9).toList()).weekLabel)
        assertEquals("第7周", entry(weeks = listOf(7)).weekLabel)
        assertEquals("", entry(weeks = emptyList()).weekLabel)
    }

    @Test
    fun `起止和步长拼周次`() {
        assertEquals(listOf(1, 2, 3), ManualEntry.weeksInRange(1, 3))
        assertEquals(listOf(2, 4, 6), ManualEntry.weeksInRange(2, 6, 2))
        assertEquals(emptyList<Int>(), ManualEntry.weeksInRange(5, 3))
    }

    @Test
    fun `改一条手动记录靠 id 认，不靠位置`() {
        val items = listOf(entry(id = "a", name = "网球"), entry(id = "b", name = "书法"))
        val replaced = items.map { if (it.id == "b") it.copy(room = "老楼201") else it }
        assertEquals(listOf("a", "b"), replaced.map { it.id })
        assertEquals("老楼201", replaced.first { it.id == "b" }.room)
        assertEquals(2, replaced.size)
    }

    @Test
    fun `手动排的周次能落到具体日期`() {
        // 第 3 周周三：起点 8月31日 + (3-1) 周 + 2 天
        assertEquals(LocalDate.of(2026, 9, 16), Term(LocalDate.of(2026, 8, 31), 20).dateOf(3, 3))
    }

    private fun sample(): String =
        javaClass.getResourceAsStream("/sample_raw_schedule.json")!!.bufferedReader().use { it.readText() }
}
