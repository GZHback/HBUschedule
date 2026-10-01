package cn.hbu.schedule.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import cn.hbu.schedule.network.WebVpnClient
import cn.hbu.schedule.network.vpnHttpClient
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val VPN = "https://v.hbu.cn"
private const val PORTAL = "$VPN/login"

/** 教务系统公开入口，未登录访问会 302 到 WebVPN 代理地址 */
private const val JW_HOME = "https://zhjw.hbu.cn/"

/** 教务代理地址形如 https://v.hbu.cn/https/<A串>/... */
private val A_PATTERN = Regex("""v\.hbu\.cn/(https?)/([0-9a-zA-Z]{20,})/""")

/** 课表数据接口特征串，等价于 kbzq.py 里 on_response 的匹配条件 */
private const val SCHEDULE_CALLBACK = "ajaxStudentSchedule/curr/callback"

private val FORM_TYPE = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

/**
 * 影子请求专用 OkHttp。必须和 WebVpnClient 用同一套证书放行 ——
 * v.hbu.cn 的证书链会让默认校验直接抛 SSLHandshakeException，之前兜底一直没生效就是这个原因。
 * 这里跟随 302：真实的 callback 请求会先被 WebVPN 跳一次，不跟随只能拿到空响应体。
 */
private val CAPTURE_CLIENT = vpnHttpClient()

/**
 * 兜底捕获状态：直取失败后用户可能在页面里手动点进了本学期课表，
 * 那时页面自己会打 callback，用 shouldInterceptRequest 再捞一次。
 */
private class Capture {
    @Volatile var delivered = false
    @Volatile var attempts = 0
    @Volatile var seen = 0
    @Volatile var diag: String? = null

    /** 页面真实发过的 callback URL（kbzq.py 就是靠观测它拿到 B 串的） */
    @Volatile var observed: String? = null

    /** 观测到真实 URL 后只自动重试一次，避免死循环 */
    @Volatile var autoRetried = false
}

private class CaptureResult(val json: String?, val diag: String?)

/**
 * 影子请求：等价于 kbzq.py 的 context.on("response")。
 * 用 OkHttp 原样重放这次 XHR（URL、请求头、Cookie 全照抄，body 为空），
 * 返回 null 不让 WebView 改道 —— WebView 自己那次请求照常完成。
 */
private fun shadowFetch(web: WebView, request: WebResourceRequest): CaptureResult {
    return try {
        val url = request.url.toString()
        val builder = Request.Builder().url(url)

        web.settings.userAgentString?.takeIf { it.isNotBlank() }
            ?.let { builder.header("User-Agent", it) }

        var hasXrw = false
        request.requestHeaders.forEach { (k, v) ->
            if (k.equals("User-Agent", true) || k.equals("Cookie", true)) return@forEach
            if (k.equals("X-Requested-With", true)) hasXrw = true
            runCatching { builder.header(k, v) }
        }
        if (!hasXrw) builder.header("X-Requested-With", "XMLHttpRequest")
        web.url?.takeIf { it.isNotBlank() }?.let { here -> runCatching { builder.header("Referer", here) } }

        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
            ?.let { builder.header("Cookie", it) }

        if (request.method.equals("POST", true)) {
            builder.method("POST", "".toRequestBody(FORM_TYPE))
        }

        CAPTURE_CLIENT.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.trimStart().startsWith("{")) {
                CaptureResult(text, null)
            } else {
                CaptureResult(null, "HTTP ${resp.code}：" + text.take(140).replace(Regex("\\s+"), " "))
            }
        }
    } catch (e: Exception) {
        CaptureResult(null, e.message)
    }
}

/**
 * WebView 登录 WebVPN：CAS 统一认证 / 账号密码 / 企业微信扫码都由网页原生完成，App 不碰登录细节。
 *
 * 登录成功后不再依赖页面 JS，而是照 `tools/capture-schedule.mjs` 的链路纯 HTTP 直取：
 * A 串 -> 课表页 HTML -> B 串 + vpn 标记 -> POST callback -> 课表 JSON。
 * 直取失败时保留 WebView 供手动操作，并用 [WebViewClient.shouldInterceptRequest] 兜底捕获。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onReady: (rawJson: String) -> Unit) {
    val ui = remember { Handler(Looper.getMainLooper()) }
    val cap = remember { Capture() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("正在打开登录页…") }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var usedToken by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }

    // 学号和密码在原生输入框里打，打完整段一次性注入页面。
    // 登录页逐键重写 input.value，光标被甩回最左边，打 2025 出来是 5202；粘贴没这问题，
    // 注入走的就是粘贴那条路径，所以绕开键盘事件。
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var oneTimeCode by remember { mutableStateOf("") }
    var revealSecret by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(true) }

    // 抓取过程的逐步日志：抓不到时必须看得见每一步的真实 HTTP 码，否则只能靠猜
    val traceBuf = remember { StringBuilder() }
    var log by remember { mutableStateOf("") }
    fun traceLine(line: String) {
        val snapshot = synchronized(traceBuf) {
            traceBuf.append(line).append('\n')
            traceBuf.toString()
        }
        ui.post { log = snapshot }
    }

    /**
     * 把原生框里的值一次写进登录页。
     *
     * 页面回来的只有「填了哪个字段、页面上有几个可用框」这类诊断，不含值本身；
     * 学号和密码只经过这一次注入，不写日志也不落盘。完整回执记进日志那块——它能滚动、
     * 能长按复制，填不上时把那段发回来就够了。
     */
    fun fillIntoPage() {
        val view = web
        if (view == null) {
            status = "登录页还没建好，等一下再点填入"
            return
        }
        view.evaluateJavascript(
            LoginInjector.script(LoginFields(account.trim(), secret, oneTimeCode.trim())),
        ) { raw ->
            val report = LoginInjector.decodeJs(raw.orEmpty())
            traceLine("填入：$report")
            if (report.startsWith("OK ")) {
                panelOpen = false
                status = "已填进页面，可以点页面里的「登录」了"
            } else {
                status = "没全填上，页面上有哪些框记在下方日志里"
            }
        }
    }

    fun finish(json: String) {
        ui.post {
            if (ready) return@post
            ready = true
            cap.delivered = true
            failed = false
            status = "已抓到课表"
            onReady(json)
        }
    }

    /** 抓取主路径：和 capture-schedule.mjs 一样，纯 HTTP，不看页面 JS 有没有执行 */
    fun fetchDirectly(knownToken: String?) {
        if (asked || ready) return
        asked = true
        usedToken = knownToken
        status = "登录成功，正在直取课表…"
        scope.launch {
            delay(700.milliseconds) // 等 CAS / WebVPN 把会话 cookie 落盘
            try {
                val cookies = CookieManager.getInstance().getCookie(VPN).orEmpty()
                traceLine(
                    "v.hbu.cn cookie " +
                        if (cookies.isBlank()) "为空（登录态没拿到）" else "已取到 ${cookies.length} 字符"
                )
                val json = WebVpnClient(
                    cookieHeader = cookies,
                    token = knownToken,
                    preferredCallback = cap.observed,
                ).fetchSchedule { line -> traceLine(line) }
                finish(json)
            } catch (e: Exception) {
                traceLine("× " + (e.message ?: "未知错误"))
                ui.post {
                    failed = true
                    cap.diag = e.message
                    status = "直取课表失败，细节见下方日志。也可在页面里手动点「选课管理 → 本学期课表」兜底"
                }
            }
        }
    }

    fun navigate(view: WebView, rawUrl: String?) {
        if (ready || rawUrl == null) return

        // 已经看到教务代理地址，A 串直接拿来用，省掉 zhjw.hbu.cn 的探测
        val hit = A_PATTERN.find(rawUrl)
        if (hit != null && !asked) {
            token = hit.groupValues[2]
            fetchDirectly(hit.groupValues[2])
            return
        }

        val uri = rawUrl.toUri()
        if (uri.host != "v.hbu.cn") return
        if ((uri.path ?: "").startsWith("/login")) {
            loggedIn = false // 回到登录页说明还没登录成功
            return
        }
        if (!loggedIn && !asked) {
            loggedIn = true
            // 顺带把教务系统首页也打开，直取失败时用户还能手动操作
            view.loadUrl(JW_HOME)
            fetchDirectly(null)
        }
    }

    fun retry() {
        failed = false
        asked = false
        cap.diag = null
        cap.attempts = 0
        synchronized(traceBuf) { traceBuf.setLength(0) }
        log = ""
        if (loggedIn) {
            status = "正在重试…"
            fetchDirectly(token)
        } else {
            status = "正在重新打开登录页…"
            web?.loadUrl(PORTAL)
        }
    }

    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }

    Surface(color = MaterialTheme.colorScheme.background) {
        // imePadding 不能省：targetSdk 36 在 Android 15+ 上被强制 edge-to-edge，
        // 此时清单里的 adjustResize 会被系统忽略、窗口不再为键盘缩小，
        // WebView 就会一直保持满屏高度、下半截藏在键盘后面。
        // 尺寸对不上之后，Chromium 每次收到输入都要"将光标滚进可视区"，
        // 表现就是打一个字页面跳一段、光标时不时被复位。
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status,
                    fontSize = 12.sp,
                    maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (failed) {
                    TextButton(onClick = { retry() }) { Text("重试", fontSize = 12.sp) }
                }
            }
            if (!ready) {
                if (panelOpen) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = account,
                                onValueChange = { account = it },
                                label = { Text("学号", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = { Text(if (revealSecret) "密码（明文）" else "密码", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation =
                                    if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = oneTimeCode,
                                onValueChange = { oneTimeCode = it },
                                label = { Text("验证码/算术答案", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { revealSecret = !revealSecret }) {
                                Text(if (revealSecret) "隐藏" else "显密码", fontSize = 11.sp)
                            }
                            TextButton(onClick = { fillIntoPage() }) { Text("填入", fontSize = 12.sp) }
                            TextButton(onClick = { panelOpen = false }) { Text("收起", fontSize = 11.sp) }
                        }
                    }
                } else {
                    // 收起来把屏幕还给网页：登录按钮和验证码图都在页面里，挤着看不清
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "输入框已收起，要重填就展开",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { panelOpen = true }) { Text("展开", fontSize = 12.sp) }
                    }
                }
            }
            if (log.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // 固定高度，不能用 heightIn：面板随日志行数长高会反复改变 WebView 尺寸，
                        // 而输入法对账时任何一次重新布局都可能把光标复位到 0
                        .height(168.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    SelectionContainer {
                        Text(
                            log,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                navigate(view, url)
                            }

                            /**
                             * 兜底拿 A 串：登录后 WebView 最终地址里就带着它，
                             * OkHttp 探测 zhjw.hbu.cn 失败时这里能补上。
                             */
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (ready) return
                                val hit = A_PATTERN.find(url.orEmpty()) ?: return
                                val a = hit.groupValues[2]
                                if (a == usedToken) return
                                failed = false
                                asked = false
                                token = a
                                fetchDirectly(a)
                            }

                            /**
                             * 兜底：页面自己在打课表接口时（例如用户手动点进本学期课表），
                             * 并发一次影子请求把 JSON 捞回来，不干扰 WebView 自己的请求。
                             */
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                cap.seen += 1
                                if (cap.delivered) return null
                                if (!url.contains(SCHEDULE_CALLBACK)) return null

                                // 页面自己发的这次请求是最权威的线索：真实方法、URL 里的 B 串都在这
                                if (cap.observed == null) {
                                    cap.observed = url
                                    traceLine(
                                        "页面真实请求 ${request.method} " + url.removePrefix(VPN) + "（B 串在这里）",
                                    )
                                    // 页面能打通就说明这个 URL 是对的，用它再直取一次
                                    if (failed && !cap.autoRetried) {
                                        cap.autoRetried = true
                                        ui.post { retry() }
                                    }
                                }
                                if (cap.attempts >= 3) return null
                                cap.attempts += 1
                                Thread {
                                    val r = shadowFetch(view, request)
                                    val json = r.json
                                    if (json != null) {
                                        finish(json)
                                    } else {
                                        val diag = r.diag ?: "未知"
                                        traceLine("影子请求失败：$diag")
                                        cap.diag = diag
                                    }
                                }.start()
                                return null
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame && (view.url ?: "").contains("zhjw.hbu.cn")) {
                                    failed = true
                                    status = "教务系统打不开（可能不在校园网）。点「重试」后再试一次"
                                }
                            }

                            // v.hbu.cn 证书链在部分网络下校验失败，仅对这两个域放行。
                            // 这是有意为之：学校自签/缺中间证书是既定事实，放行范围已收窄到两个校内域名，
                            // 其余域名一律 cancel。lint 的告警在这里属于已知取舍，故显式抑制。
                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: SslError,
                            ) {
                                val host = (error.url ?: "").toUri().host.orEmpty()
                                if (host == "v.hbu.cn" || host == "zhjw.hbu.cn") handler.proceed() else handler.cancel()
                            }
                        }
                        loadUrl(PORTAL)
                    }.also { web = it }
                },
                onRelease = { it.destroy() },
                // 用 weight 明确吃掉剩余空间：fillMaxSize 在 Column 里表达的是同一件事，
                // 但配上上面会变高的兄弟节点时容易产生多余的重新布局
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
