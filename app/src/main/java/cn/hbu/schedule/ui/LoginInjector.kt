package cn.hbu.schedule.ui

/** 要填进登录页的字段。留空的不填，所以登录失败后可以只补验证码。 */
data class LoginFields(
    val account: String = "",
    val password: String = "",
    val code: String = "",
)

/**
 * 把学号 / 密码 / 验证码一次性写进登录页。
 *
 * 为什么要绕：教务和 CAS 的登录页逐键输入时会重写 input.value，光标被甩回最左边，
 * 打 2025 出来是 5202；而粘贴是好的，说明它只拦键盘事件。所以这里一次赋值 + 派发
 * input/change，走的就是粘贴那条路径，刻意不发 keyup/keydown（那正是页面接手的地方）。
 *
 * 赋值要经过 HTMLInputElement.prototype 上的原生 setter：直接 el.value = x 会被 Vue 的
 * v-model 当成「值没变」忽略掉，它自己缓存了 _value。
 */
object LoginInjector {

    /** 生成给 WebView.evaluateJavascript 的一整段表达式 */
    fun script(fields: LoginFields): String =
        HEAD + quote(fields.account) + MID_ACCOUNT + quote(fields.password) +
            MID_PASSWORD + quote(fields.code) + TAIL

    /** evaluateJavascript 回调给的是 JSON 字面量，脱掉外层引号即可 */
    fun decodeJs(result: String): String = result.removeSurrounding("\"")

    /**
     * 转成 JS 字符串字面量。
     *
     * 手写而不用 org.json：单元测试跑在 JVM 上，android.jar 里的 org.json 是会抛异常的桩。
     * < > & 和 U+2028 / U+2029 也一起转义：前者免得这段脚本被当成 HTML 片段处理，
     * 后者在 ES2019 之前是字符串字面量里的非法字符。
     */
    fun quote(raw: String): String {
        val out = StringBuilder(raw.length + 2)
        out.append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                else -> when {
                    ch == '<' || ch == '>' || ch == '&' -> hexEscape(out, ch)
                    ch.code < 0x20 || ch.code == 0x2028 || ch.code == 0x2029 -> hexEscape(out, ch)
                    else -> out.append(ch)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    // 按码点写转义，源码里不出现真正的行分隔符
    private fun hexEscape(out: StringBuilder, ch: Char) {
        out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
    }

    private const val HEAD = """
(function () {
  var want = {account: """

    private const val MID_ACCOUNT = """, password: """

    private const val MID_PASSWORD = """, code: """

    /** ES5 写法：老设备的 WebView 也吃得下。字段一律按类型和 name 找，不依赖页面里的 id。 */
    private const val TAIL = """
};
  var lower = function (s) { return String(s || '').toLowerCase(); };
  var attr = function (el, key) { return el.getAttribute(key) || ''; };
  var fieldName = function (el) { return lower(attr(el, 'name')); };
  var inputs = [].slice.call(document.querySelectorAll('input'));
  var shown = function (el) {
    if (el.disabled || el.readOnly) { return false; }
    var box = el.getBoundingClientRect();
    var style = window.getComputedStyle(el);
    return box.width > 1 && box.height > 1 &&
        style.visibility !== 'hidden' && style.display !== 'none';
  };
  var visible = inputs.filter(shown);
  // 页面里有 type=hidden 的 username（二次验证用），别靠尺寸判断把它当输入框
  var usable = visible.filter(function (el) { return lower(el.type) !== 'hidden'; });
  var ACCOUNT_NAMES = ['username', 'user_name', 'useraccount', 'account', 'user_id', 'jusername', 'uname', 'loginid', 'uid'];
  var CODE_NAMES = ['captcha', 'captcha_code', 'verifycode', 'verify_code', 'sms_code', 'phone_code', 'code', 'dynamicpassword'];
  var TEXTY = ['text', 'email', 'tel', 'number'];
  var has = function (list, name) { return list.indexOf(name) >= 0; };
  var inText = function (el, words) {
    var hint = attr(el, 'placeholder') + ' ' + attr(el, 'aria-label');
    for (var i = 0; i < words.length; i++) { if (hint.indexOf(words[i]) >= 0) { return true; } }
    return false;
  };
  var isPassword = function (el) { return lower(el.type) === 'password'; };
  // 改密码那张表也是 type=password，填错地方比填不上更糟，宁可报「没找到」
  var isLoginPassword = function (el) {
    var name = fieldName(el);
    if (!isPassword(el)) { return false; }
    if (name.indexOf('new') >= 0 || name.indexOf('confirm') >= 0 || name.indexOf('old') >= 0) { return false; }
    return !inText(el, ['新密码', '再次']);
  };
  var isCode = function (el) {
    return !isPassword(el) && (has(CODE_NAMES, fieldName(el)) || inText(el, ['验证码', '校验码']));
  };
  var tagOf = function (el) {
    var name = fieldName(el) || lower(el.id) || lower(el.type);
    return String(name).replace(/[^A-Za-z0-9_.-]/g, '').slice(0, 24) || 'input';
  };
  var fire = function (el, type) {
    if (typeof Event === 'function') { el.dispatchEvent(new Event(type, { bubbles: true })); }
    else { var ev = document.createEvent('HTMLEvents'); ev.initEvent(type, true, true); el.dispatchEvent(ev); }
  };
  var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  var put = function (el, value) {
    setter.call(el, value);
    fire(el, 'input');
    fire(el, 'change');
  };
  var first = function (tests) {
    for (var i = 0; i < tests.length; i++) {
      for (var j = 0; j < usable.length; j++) {
        var el = usable[j];
        if (tests[i](el)) { return el; }
      }
    }
    return null;
  };
  var account = first([
    function (el) { return !isCode(el) && has(ACCOUNT_NAMES, fieldName(el)); },
    function (el) { return !isCode(el) && !isPassword(el) && inText(el, ['用户名', '学号', '账号', '工号']); },
    function (el) { return !isPassword(el) && !isCode(el) && has(TEXTY, lower(el.type)); }
  ]);
  var password = first([
    function (el) { return isPassword(el) && fieldName(el) === 'password'; },
    isLoginPassword
  ]);
  var code = first([isCode]);
  var filled = [];
  var missed = [];
  var take = function (label, el, value) {
    if (!value) { return; }
    if (el) { put(el, value); filled.push(label + '=' + tagOf(el)); }
    else { missed.push(label); }
  };
  take('学号', account, want.account);
  take('密码', password, want.password);
  take('验证码', code, want.code);
  if (!filled.length && !missed.length) { return '三个框都是空的，没要填的东西'; }
  if (!visible.length) {
    return '这页面上没有可用输入框（共 ' + inputs.length + ' 个全不显示）：表单可能还没渲染或在 iframe 里，'
        + '先点一下页面，或改用「账号登录」那个标签';
  }
  var summary = visible.length + ' 个可用输入框，' + (filled.length ? '已填 ' + filled.join('、') : '一个都没填成');
  if (missed.length) { summary = summary + '，没找到 ' + missed.join('、'); }
  return summary;
})()""";
}
