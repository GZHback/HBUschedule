package cn.hbu.schedule.network

import cn.hbu.schedule.data.SchoolWeekProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val VPN = "https://v.hbu.cn"

/** 教务系统公开入口：未登录访问会 302 到 WebVPN 代理地址，Location 里就带 A 串 */
private const val JW_HOME = "https://zhjw.hbu.cn/"

private const val CURRICULUM_TAIL = "student/courseSelect/thisSemesterCurriculum/index"

private const val CALLBACK_TAIL = "ajaxStudentSchedule/curr/callback"

/** 浏览器 UA，避免被 WebVPN 识别为脚本流量 */
private const val UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

private val FORM = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

/** 教务代理地址形如 https://v.hbu.cn/https/<A串>/... */
private val A_PATTERN = Regex("""v\.hbu\.cn/(https?)/([0-9a-zA-Z]{20,})/""")

/** 课表页里写死的 callback 地址（B 串就在里面） */
private val CALLBACK_IN_PAGE = Regex("""(["'])([^"']*ajaxStudentSchedule/curr/callback)\1""")

/** 兜底：课表页里出现过的 B 串 */
private val B_FIELD = Regex("""thisSemesterCurriculum/([0-9a-zA-Z]{10,})""")

/** WebVPN 给 POST 地址追加的标记 */
private val VPN_MARK = Regex("""vpn-\d+-o\d+-zhjw\.hbu\.cn""")

private const val DEFAULT_MARK = "vpn-12-o2-zhjw.hbu.cn"

/** 单次 HTTP 往返。必须保留状态码 —— 302 空体这种症状全靠它才能看出来 */
private class Round(val url: String, val code: Int, val body: String)

private class Raw(val code: Int, val location: String?, val body: String)

/**
 * v.hbu.cn / zhjw.hbu.cn 的证书链在部分网络下校验不通过（WebView 那边也是靠 handler.proceed() 放行的），
 * 所以访问这两个域的 OkHttp 客户端统一从这里构造，避免有的地方放行、有的地方没放行。
 */
fun vpnHttpClient(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val ssl = SSLContext.getInstance("TLS")
    ssl.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    return builder
        .sslSocketFactory(ssl.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * WebVPN 课表抓取客户端，链路完全复刻 `tools/capture-schedule.mjs`：
 *
 * 1. **A 串**：GET `https://zhjw.hbu.cn/` 的 302 `Location` 里就有（和会话无关，按目标主机固定）；
 *    WebView 里已经看到过 A 串就直接传进来，省掉这一步。
 * 2. **课表页 HTML**：GET `<base>/student/courseSelect/thisSemesterCurriculum/index`，
 *    带 WebView 登录后拿到的 v.hbu.cn 会话 cookie。
 * 3. **B 串 / 标记**：从 HTML 里正则出 callback 地址（B 串在其中），以及 `?vpn-N-oM-<内网主机>` 标记。
 * 4. **课表 JSON**：POST callback（空 body）拿 `xkxx` 结构；被拦就补上标记再来一次。
 *
 * 全程纯 HTTP，不依赖页面 JS 是否执行、XHR 是否被劫持，所以没有任何时机问题。
 */
class WebVpnClient(
    cookieHeader: String,
    private val token: String? = null,
    private val proto: String = "https",
    /** WebView 网络层观测到的真实 callback URL（页面自己发过的那个），优先级最高 */
    private val preferredCallback: String? = null,
) {

    private val cookie: String = cookieHeader

    /**
     * 课表页 HTML 里教务自己标的当前周次，抓取过程中顺带读出来的。
     * 读不到就是 null —— 页面可能根本没渲染这个数，这时学期起点继续用学生自己设的值。
     */
    @Volatile
    var schoolWeek: Int? = null
        private set

    private val client: OkHttpClient =
        vpnHttpClient(OkHttpClient.Builder().followRedirects(false))

    /** 抓取课表原始 JSON（含 xkxx 结构）；失败抛异常，每一步的 HTTP 结果通过 [trace] 回传 */
    suspend fun fetchSchedule(trace: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val (p, a) = resolvePrefix(trace)
        val base = "$VPN/$p/$a"
        val pageUrl = "$base/$CURRICULUM_TAIL"

        val first = round(pageUrl, "GET", trace)
        var html = first.body
        val mark = VPN_MARK.find(html)?.value ?: DEFAULT_MARK
        trace("课表页 GET -> HTTP ${first.code}，${html.length} 字节，标记=$mark")

        if (!looksLikeCurriculum(html)) {
            // 不带 ?vpn-N-oM-<内网主机> 时会被打回，补上标记再来一次
            val again = round("$pageUrl?$mark", "GET", trace)
            trace("课表页带标记 GET -> HTTP ${again.code}，${again.body.length} 字节")
            if (again.body.length > html.length) html = again.body
        }
        if (html.isBlank()) {
            throw IllegalStateException("课表页返回空内容，WebVPN 登录态可能已失效，请重新登录")
        }

        schoolWeek = SchoolWeekProbe.detect(html)
        trace(
            "教务页面的当前周次：" +
                (schoolWeek?.let { "第 $it 周" } ?: "页面里没读到，周次继续按「学期设置」算")
        )

        val b = B_FIELD.find(html)?.groupValues?.get(1)
        trace("课表页里的 B 串：" + (b ?: "没出现（B 只出现在 callback 请求 URL 里，属正常）"))

        val candidates = callbackCandidates(base, html, b)
        if (candidates.isEmpty()) {
            throw IllegalStateException("课表页里既没有 callback 地址也没有 B 串。页面开头：" + preview(html))
        }
        trace("callback 候选 ${candidates.size} 个")

        for (raw in candidates) {
            val urls = if (raw.contains('?')) listOf(raw) else listOf("$raw?$mark", raw)
            for (u in urls) {
                for (method in listOf("POST", "GET")) {
                    val r = round(u, method, trace)
                    trace("$method ${r.url.removePrefix(VPN)} -> HTTP ${r.code}，${r.body.length} 字节")
                    if (r.body.trimStart().startsWith("{")) {
                        trace("✓ 命中课表 JSON")
                        return@withContext r.body
                    }
                }
            }
        }
        throw IllegalStateException("callback 的所有候选都没返回 JSON，请看上面每一步的 HTTP 码")
    }

    /** 候选 callback 地址：WebView 观测到的最优先，其次是页面里引用的，最后按 B 串拼 */
    private fun callbackCandidates(base: String, html: String, b: String?): List<String> {
        val out = LinkedHashSet<String>()
        preferredCallback?.takeIf { it.isNotBlank() }?.let { out += it }

        val inPage = CALLBACK_IN_PAGE.find(html)?.groupValues?.get(2)
        if (inPage != null) {
            if (inPage.startsWith("http")) {
                out += inPage
            } else {
                // 页面里可能是相对路径，也可能以 / 开头；各种基址都解析一遍
                for (root in listOf("$base/student/courseSelect/", "$base/", "$VPN/")) {
                    runCatching { URL(URL(root), inPage).toString() }.getOrNull()?.let { out += it }
                }
            }
        }
        if (b != null) {
            out += "$base/student/courseSelect/thisSemesterCurriculum/$b/$CALLBACK_TAIL"
        }
        return out.toList()
    }

    /** 拿 A 串：优先用调用方给的，其次靠 zhjw.hbu.cn 的 302 */
    private fun resolvePrefix(trace: (String) -> Unit): Pair<String, String> {
        token?.takeIf { it.isNotBlank() }?.let {
            trace("A 串（来自 WebView 地址栏）= $it")
            return proto to it
        }

        val loc = try {
            locationOf(JW_HOME)
        } catch (e: Exception) {
            throw IllegalStateException("访问 zhjw.hbu.cn 失败：${e.message}")
        }
        val hit = A_PATTERN.find(loc)
            ?: throw IllegalStateException("zhjw.hbu.cn 没返回 302 跳转，拿不到 A 串（Location=$loc）")
        trace("A 串（来自 zhjw.hbu.cn 的 302）= ${hit.groupValues[2]}")
        return hit.groupValues[1] to hit.groupValues[2]
    }

    /** 这个 HTML 看起来是课表页而不是被打回的首页/登录页 */
    private fun looksLikeCurriculum(html: String): Boolean =
        html.contains("ajaxStudentSchedule") || B_FIELD.containsMatchIn(html)

    private fun base(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        return builder
    }

    /** 只要 Location 头，用于从 zhjw.hbu.cn 的 302 里取 A 串 */
    private fun locationOf(url: String): String =
        client.newCall(base(url).get().build()).execute().use { it.header("Location").orEmpty() }

    /** 单次请求，不跟随跳转 */
    private fun once(url: String, method: String): Raw {
        val builder = base(url).header("X-Requested-With", "XMLHttpRequest")
        if (method == "POST") builder.post("".toRequestBody(FORM)) else builder.get()
        client.newCall(builder.build()).execute().use { resp ->
            return Raw(resp.code, resp.header("Location"), resp.body?.string().orEmpty())
        }
    }

    /**
     * 发请求并手动跟随 3xx。WebVPN 靠 302 做跳转，必须把跳转过程记下来：
     * 之前「响应体为空」的症状就是跳转被跟丢了，看不到真实状态码。
     */
    private fun round(url: String, method: String, trace: (String) -> Unit): Round {
        var cur = url
        repeat(5) {
            val r = once(cur, method)
            val loc = r.location
            if (r.code in 300..399 && !loc.isNullOrBlank()) {
                trace("  重定向 ${r.code} -> ${loc.take(70)}")
                cur = runCatching { URL(URL(cur), loc).toString() }.getOrDefault(loc)
                return@repeat
            }
            return Round(cur, r.code, r.body)
        }
        return Round(cur, -1, "")
    }

}

private fun preview(s: String): String = s.take(200).replace(Regex("""\s+"""), " ")
