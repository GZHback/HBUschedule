package cn.hbu.schedule

import cn.hbu.schedule.data.Calibration
import cn.hbu.schedule.data.SchoolWeekHint
import cn.hbu.schedule.data.SchoolWeekProbe
import cn.hbu.schedule.data.TermSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 「今天是第几周」这件事的全部依据：一个起点日期。
 * 这些测试盯的就是它会不会被读坏、会不会被一个不靠谱的网页数字改错。
 */
class TermSettingsTest {

    private val anchor = LocalDate.of(2026, 8, 31)

    @Test
    fun `起点存和读回来一样`() {
        val settings = TermSettings(anchor, 20)
        assertEquals(settings, TermSettings.decode(TermSettings.encode(settings)))
    }

    @Test
    fun `读不出名堂就退回默认起点`() {
        assertEquals(TermSettings.DEFAULT, TermSettings.decode(null))
        assertEquals(TermSettings.DEFAULT, TermSettings.decode("2026-08-31"))
        assertEquals(TermSettings.DEFAULT, TermSettings.decode("乱七八糟|二十"))
        assertEquals(TermSettings.DEFAULT, TermSettings.decode("2026-08-31|0"))
        assertEquals(TermSettings.DEFAULT, TermSettings.decode("2026-08-31|99"))
    }

    @Test
    fun `本学期按核对过的起点算`() {
        val term = TermSettings(anchor, 20).toTerm()
        // 教务页面在 2026-09-28 显示「第 5 周 星期一」，起点就按这个核对出来的
        assertEquals(5, term.weekOf(LocalDate.of(2026, 9, 28)))
        assertEquals(5, term.weekOf(LocalDate.of(2026, 10, 1)))
        assertEquals(1, term.weekNumber(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `学期外给 null 但也告诉你是第几周`() {
        val term = TermSettings(anchor, 20).toTerm()
        // 下学期开学还按秋天学期起算，会算出第 25 周 —— 界面要如实说，不能退回第 1 周装没事
        assertNull(term.weekOf(LocalDate.of(2027, 2, 16)))
        assertEquals(25, term.weekNumber(LocalDate.of(2027, 2, 16)))
        // 起点设到以后去了，今天是第 0 周
        assertEquals(0, term.weekNumber(LocalDate.of(2026, 8, 25)))
    }

    @Test
    fun `教务页面上写了本周第几就认，光秃秃的周次不认`() {
        assertEquals(6, SchoolWeekProbe.detect("<div>本周 第6周</div>"))
        assertEquals(6, SchoolWeekProbe.detect("当前周次：6"))
        assertEquals(6, SchoolWeekProbe.detect("""{"currentWeek":"6"}"""))
        // 格子里到处是「第7周」这种周次文案，拿它当锚点会把对的设置改错
        assertNull(SchoolWeekProbe.detect("<td>第7周</td><td>3-14周</td>"))
        assertNull(SchoolWeekProbe.detect(""))
        assertNull(SchoolWeekProbe.detect(null))
        assertNull(SchoolWeekProbe.detect("本周没有数字"))
    }

    @Test
    fun `读到的周次能倒推第1周周一`() {
        val monday = SchoolWeekProbe.week1MondayOf(LocalDate.of(2026, 10, 8), 6)
        // 10月8日（周四）是第 6 周 -> 第 1 周周一就是 8月31日，正好是核对过的那个起点
        assertEquals(anchor, monday)
        assertEquals(anchor.plusWeeks(1), SchoolWeekProbe.week1MondayOf(LocalDate.of(2026, 10, 8), 5))
    }

    @Test
    fun `任何日期都先收到周一再倒推`() {
        // 周三、周日都落回第 1 周的周一
        assertEquals(anchor, SchoolWeekProbe.alignToMonday(LocalDate.of(2026, 9, 3)))
        assertEquals(anchor, SchoolWeekProbe.alignToMonday(LocalDate.of(2026, 9, 6)))
        // 下周一才是下一周
        assertEquals(anchor.plusWeeks(1), SchoolWeekProbe.alignToMonday(LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `只在教务的周次和算出来的不一样时才提示`() {
        val settings = TermSettings(anchor, 20)
        val today = LocalDate.of(2026, 10, 1)
        assertNull(Calibration.between(SchoolWeekHint(5, today), settings, today))

        // 教务说 10月1日 是第 6 周，我们算的是第 5 周：那它的起点比我们早一周
        val calibration = Calibration.between(SchoolWeekHint(6, today), settings, today)
        assertNotNull(calibration)
        assertEquals(6, calibration?.schoolWeek)
        assertEquals(5, calibration?.ourWeek)
        assertEquals(anchor.minusWeeks(1), calibration?.week1Monday)

        // 提示放久了不该再来烦人
        assertNull(Calibration.between(SchoolWeekHint(6, LocalDate.of(2026, 9, 1)), settings, today))
        // 没读到就当没事
        assertNull(Calibration.between(null, settings, today))
    }

    @Test
    fun `提示本身也存得回来`() {
        val hint = SchoolWeekHint(6, LocalDate.of(2026, 10, 1))
        assertEquals(hint, SchoolWeekHint.decode(SchoolWeekHint.encode(hint)))
        assertNull(SchoolWeekHint.decode("坏"))
        assertNull(SchoolWeekHint.decode(null))
    }
}
