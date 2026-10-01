package cn.hbu.schedule.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测「值怎么被封进 JS 字面量」这一层。
 *
 * 挑字段的逻辑跑在页面里，用一份手写假 DOM 单独验过（可见性、type=hidden 的 username、
 * 改密码表单、只补验证码这些分支）；这里守着的是另一头：任意凭据字符都不能逃出字符串字面量。
 */
class LoginInjectorTest {

    private val samples = listOf(
        "2025123456",
        "带\"引号",
        "反斜杠 \\ 结尾 \\",
        "换行\n制表\t退格\b",
        "尖括号 <b>&amp;</b>",
        "行分隔符 " + 0x2028.toChar() + " 和 " + 0x2029.toChar(),
        "控制符 " + 0x01.toChar() + 0x1F.toChar(),
        "中文密码 河大",
        "",
    )

    /** JS 的字符串字面量在这套转义下和 JSON 等价，所以能解回原值就说明既没截断也没逃逸 */
    @Test
    fun `凭据里的任何字符都封成一个合法字面量并原样解回`() {
        for (raw in samples) {
            val literal = LoginInjector.quote(raw)
            assertEquals("不该多出裸换行：" + raw, -1, literal.indexOf('\n'))
            assertEquals("不该多出裸回车：" + raw, -1, literal.indexOf('\r'))
            assertEquals(
                "解回来必须还是原值：" + raw,
                raw,
                Json.parseToJsonElement(literal).jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `尖括号和与号写成码点，不会被当成标记`() {
        val literal = LoginInjector.quote("a<b>&c")
        assertFalse(literal.contains('<'))
        assertFalse(literal.contains('>'))
        assertFalse(literal.contains('&'))
        assertEquals("a\u003Cb\u003E\u0026c", Json.parseToJsonElement(literal).jsonPrimitive.content)
    }

    @Test
    fun `中文按原样留着，出问题时看得懂`() {
        assertEquals("\"河大\"", LoginInjector.quote("河大"))
    }

    @Test
    fun `三个值各自进一个字面量，空的也不能漏键`() {
        val line = LoginInjector.script(LoginFields(account = "20250000001"))
            .lineSequence().first { it.contains("var want =") }
        assertTrue(line.contains(LoginInjector.quote("20250000001")))
        assertTrue(line.endsWith("code: " + LoginInjector.quote("")))
    }

    @Test
    fun `只派发粘贴那条路径上的事件`() {
        val js = LoginInjector.script(LoginFields("a", "b", "c"))
        assertFalse(js.contains("keyup"))
        assertFalse(js.contains("keydown"))
        assertTrue(js.contains("fire(el, 'input')"))
        assertTrue(js.contains("fire(el, 'change')"))
        assertTrue(js.trimEnd().endsWith("})()"))
    }

    @Test
    fun `页面回执脱掉外层引号`() {
        assertEquals("已填 学号=username", LoginInjector.decodeJs("\"已填 学号=username\""))
    }
}
