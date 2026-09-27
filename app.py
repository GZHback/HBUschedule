"""河北大学课表本地工具：双击运行 -> 浏览器看周课表 -> 一键导出 .ics 到系统日历。

    python3 app.py            # 用已抓好的数据打开界面
    python3 app.py --refresh  # 先跑 Playwright 扫码抓一次新课表

不联网、不装第三方库，数据只存在你自己电脑上。
"""

import argparse
import datetime as dt
import json
import os
import subprocess
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import ics_export
import schedule_model

ROOT = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.join(ROOT, "web")
SETTINGS_PATH = os.path.join(ROOT, "settings.json")
DEFAULT_SCHEDULE_FILES = ("schedule_out.json", "schedule.json",
                          os.path.join("docs", "sample_raw_schedule.json"))
DEFAULTS = {"week1_monday": "2026-09-07", "total_weeks": 20}

_lock = threading.Lock()
_state = {"courses": [], "source": None, "refresh": None}


def load_settings():
    settings = dict(DEFAULTS)
    if os.path.exists(SETTINGS_PATH):
        try:
            with open(SETTINGS_PATH, encoding="utf-8") as f:
                settings.update(json.load(f))
        except (json.JSONDecodeError, OSError):
            pass
    return settings


def save_settings(settings):
    with open(SETTINGS_PATH, "w", encoding="utf-8") as f:
        json.dump(settings, f, ensure_ascii=False, indent=2)


def find_schedule_file():
    for name in DEFAULT_SCHEDULE_FILES:
        path = os.path.join(ROOT, name)
        if os.path.exists(path):
            return path
    return None


def set_courses(courses, source):
    _state["courses"] = courses
    _state["source"] = source


def read_courses():
    path = find_schedule_file()
    if not path:
        return None, "还没有课表数据：点“抓取课表”，或用“载入文件”选一份教务导出的 JSON"
    return schedule_model.load_courses(path), os.path.relpath(path, ROOT)


def semester_start(settings):
    return dt.datetime.strptime(settings["week1_monday"], "%Y-%m-%d").date()


def api_payload():
    settings = load_settings()
    courses = _state["courses"]
    week1 = semester_start(settings)
    today = dt.date.today()
    current_week = (today - week1).days // 7 + 1
    return {
        "settings": settings,
        "source": _state["source"],
        "course_count": len(courses),
        "current_week": current_week if 1 <= current_week <= settings["total_weeks"] else None,
        "week1_monday": week1.isoformat(),
        "courses": [c.to_dict() for c in courses],
        "session_times": schedule_model.SESSION_TIMES,
        "week_names": [schedule_model.WEEK_CN[i] for i in range(1, 8)],
    }


def export_ics():
    settings = load_settings()
    warnings = []
    text = ics_export.to_ics(_state["courses"], semester_start(settings),
                             total_weeks=int(settings["total_weeks"]), warn=warnings)
    return text, warnings


def run_scraper():
    """跑 kbzq.py（会弹出浏览器扫码），抓完把 schedule_out.json 读进来。"""
    target = os.path.join(ROOT, "kbzq.py")
    if not os.path.exists(target):
        raise FileNotFoundError("找不到 kbzq.py")
    proc = subprocess.Popen([sys.executable, target], cwd=ROOT)
    _state["refresh"] = proc
    return proc


def refresh_done():
    proc = _state.get("refresh")
    if proc is None:
        return None
    if proc.poll() is None:
        return "running"
    _state["refresh"] = None
    if proc.returncode != 0:
        return "failed"
    courses, source = read_courses()
    if courses is None:
        return "failed"
    set_courses(courses, source)
    return "ok"


class Handler(BaseHTTPRequestHandler):
    server_version = "HBUschedule/0.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("  %s\n" % (fmt % args))

    def _send(self, code, body, ctype="application/json; charset=utf-8", extra=None):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def _json(self, obj, code=200):
        self._send(code, json.dumps(obj, ensure_ascii=False))

    def _body_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            return self._static("index.html")
        if path.startswith("/static/"):
            return self._static(path[len("/static/"):])
        if path == "/api/state":
            return self._json(api_payload())
        if path == "/api/export.ics":
            with _lock:
                try:
                    text, warnings = export_ics()
                except ValueError as exc:
                    return self._json({"error": str(exc)}, 400)
            name = "hbu-schedule.ics"
            return self._send(200, text, "text/calendar; charset=utf-8",
                              {"Content-Disposition": f'attachment; filename="{name}"',
                               "X-Export-Warnings": str(len(warnings))})
        if path == "/api/refresh-status":
            return self._json({"status": refresh_done()})
        self._json({"error": "not found"}, 404)

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/settings":
            try:
                body = self._body_json()
            except json.JSONDecodeError:
                return self._json({"error": "请求体不是合法 JSON"}, 400)
            settings = load_settings()
            for key in ("week1_monday", "total_weeks"):
                if key in body:
                    settings[key] = body[key]
            try:
                semester_start(settings)
                settings["total_weeks"] = max(1, min(30, int(settings["total_weeks"])))
            except (ValueError, TypeError):
                return self._json({"error": "学期参数不合法"}, 400)
            save_settings(settings)
            return self._json({"ok": True, "settings": settings})

        if path == "/api/load":
            try:
                body = self._body_json()
            except json.JSONDecodeError:
                return self._json({"error": "请求体不是合法 JSON"}, 400)
            text = body.get("raw", "")
            try:
                data = json.loads(text)
            except json.JSONDecodeError as exc:
                return self._json({"error": f"JSON 解析失败：{exc}"}, 400)
            with _lock:
                try:
                    if isinstance(data, dict) and "xkxx" in data:
                        courses = schedule_model.normalize(data)
                    elif isinstance(data, list) and data and "name" in data[0]:
                        courses = schedule_model.courses_from_dicts(data)
                    elif isinstance(data, dict) and "courses" in data:
                        courses = schedule_model.courses_from_dicts(data["courses"])
                    elif isinstance(data, list):
                        courses = schedule_model.courses_from_dicts(
                            schedule_model._upgrade_legacy_list(data))
                    else:
                        return self._json({"error": "无法识别的课表 JSON 结构"}, 400)
                except Exception as exc:
                    return self._json({"error": f"解析课表出错：{exc}"}, 400)
                set_courses(courses, "手动载入")
            return self._json({"ok": True, "course_count": len(courses)})

        if path == "/api/refresh":
            with _lock:
                if _state.get("refresh") is not None:
                    return self._json({"status": "running"})
                try:
                    run_scraper()
                except FileNotFoundError as exc:
                    return self._json({"error": str(exc)}, 400)
            return self._json({"status": "started"})

        self._json({"error": "not found"}, 404)

    def _static(self, rel):
        full = os.path.normpath(os.path.join(WEB, rel))
        if not full.startswith(WEB) or not os.path.isfile(full):
            return self._json({"error": "not found"}, 404)
        ctype = ("text/html; charset=utf-8" if full.endswith(".html")
                 else "text/css; charset=utf-8" if full.endswith(".css")
                 else "application/javascript; charset=utf-8")
        with open(full, encoding="utf-8") as f:
            self._send(200, f.read(), ctype)


def main():
    parser = argparse.ArgumentParser(description="河北大学课表本地工具")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--refresh", action="store_true", help="启动前先跑一次抓取")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()

    courses, source = read_courses()
    if courses is None:
        print(f"ℹ️ {source}")
    else:
        set_courses(courses, source)
        print(f"✅ 已载入 {len(courses)} 门课（{source}）")

    if args.refresh:
        run_scraper()
        print("🌐 已启动抓取，请在弹出的浏览器里扫码登录…")

    url = f"http://127.0.0.1:{args.port}/"
    print(f"🚀 课表界面：{url}（Ctrl+C 退出）")
    if not args.no_browser:
        threading.Timer(0.4, lambda: webbrowser.open(url)).start()
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
