import re
import json
import time
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeoutError

# ================= 配置 =================
BASE_VPN = "https://v.hbu.cn"
HEADLESS = False   # 需要扫码登录，保持有头模式
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
# =======================================


def get_schedule_by_playwright():
    """扫码登录 WebVPN -> 进入教务系统 -> 监听 callback 响应 -> 返回课表 JSON"""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=HEADLESS, channel="msedge")
        context = browser.new_context(user_agent=UA)
        page = context.new_page()

        captured = {"data": None, "url": None}

        # 监听所有响应，一旦命中课表接口（不管页面用 GET 还是 POST）直接抓 JSON
        def on_response(resp):
            if "ajaxStudentSchedule/curr/callback" in resp.url:
                try:
                    captured["data"] = resp.json()
                except Exception:
                    try:
                        captured["data"] = json.loads(resp.text())
                    except Exception as e:
                        print(f"⚠️ callback 响应解析失败: {e}")
                        return
                captured["url"] = resp.url
                print(f"✅ 已捕获课表接口响应: {resp.url}")

        # 监听 context 级别的响应：教务系统可能在新标签页打开，
        # page 级监听器收不到新标签页的响应，必须挂在整个 context 上
        context.on("response", on_response)

        # 1) 打开 WebVPN，扫码登录
        page.goto(BASE_VPN)
        print("🌐 请在弹出的 Edge 窗口中扫码登录 WebVPN ...")

        # 2) 等登录成功：登录后默认在“图书资源”页，会出现“选课教务系统学生端入口”字样
        page.get_by_text(re.compile(r"选课教务系统学生端入口")).first.wait_for(timeout=180000)
        print("✅ WebVPN 登录成功")

        # 3) 切换到左侧“业务应用”分类
        page.get_by_text("业务应用", exact=True).first.click()
        print("📂 已切换到 业务应用 分类")

        # 4) 点击“本科教务学生端”卡片
        #    用副标题 zhjw.hbu.cn 精确定位，避免误点“图书资源”页里的 10.179.0.x IP 入口
        card = page.get_by_text(re.compile(r"zhjw\.hbu\.cn")).first
        card.wait_for(timeout=15000)
        print("✅ 找到 本科教务学生端(zhjw.hbu.cn)卡片")

        # 5) 点击入口（可能在新标签页打开）
        work_page = None
        try:
            with context.expect_page(timeout=8000) as info:
                card.click()
            work_page = info.value
            print("📌 教务系统在新标签页打开")
        except PWTimeoutError:
            work_page = page
            print("📌 教务系统在当前页打开")

        # 4) 等跳转出 /http|https/{A}/ 结构，提取“协议 + A 串”
        work_page.wait_for_url(
            re.compile(r"v\.hbu\.cn/(?:http|https)/[^/]+/"), timeout=60000
        )
        cur = work_page.url
        m = re.search(r"v\.hbu\.cn/(http|https)/([^/]+)/", cur)
        if not m:
            raise RuntimeError(f"❌ 提取 A 串失败，当前 URL: {cur}")
        protocol, A = m.group(1), m.group(2)
        print(f"✅ A = {A}(协议 = {protocol})")

        # 5) 打开本学期课表页面（页面会自动发起 callback 请求）
        curriculum_url = (
            f"{BASE_VPN}/{protocol}/{A}/student/courseSelect/"
            "thisSemesterCurriculum/index"
        )
        print(f"🔗 打开课表页: {curriculum_url}")
        work_page.goto(curriculum_url, wait_until="domcontentloaded", timeout=60000)

        # 注意：课表页 URL 始终是 .../thisSemesterCurriculum/index，页面地址里没有 B 串；
        # B 串只出现在 callback 请求的 URL 中。所以不用等 URL 变化，
        # 页面加载后会自动发起 callback 请求，直接等监听器捕获即可。
        deadline = time.time() + 60
        while time.time() < deadline and captured["data"] is None:
            work_page.wait_for_timeout(500)

        # 兜底：若未自动捕获，刷新页面再触发一次
        if captured["data"] is None:
            print("💡 未自动捕获，尝试刷新页面重新触发 callback ...")
            work_page.reload(wait_until="domcontentloaded", timeout=60000)
            deadline = time.time() + 30
            while time.time() < deadline and captured["data"] is None:
                work_page.wait_for_timeout(500)

        # 从捕获到的 callback URL 中提取 B 串（仅展示用）
        if captured["url"]:
            mB = re.search(r"thisSemesterCurriculum/([^/]+)/ajaxStudentSchedule", captured["url"])
            if mB:
                print(f"✅ B = {mB.group(1)}（从 callback URL 提取）")

        browser.close()

        if captured["data"] is None:
            raise RuntimeError("❌ 未能捕获课表 JSON, 请检查页面是否正常加载")

        return captured["data"]


def parse_courses(raw_data: dict):
    result = []
    xkxx_list = raw_data.get("xkxx", [])
    # 接口真实结构: xkxx = [ {"课程编号": 课程详情对象}, ... ]
    for item_dict in xkxx_list:
        for _, course_info in item_dict.items():
            base = {
                "课程名称": course_info.get("courseName", "").strip(),
                "教师": course_info.get("attendClassTeacher", "").strip(),
                "课程性质": course_info.get("coursePropertiesName", ""),
                "课程类别": course_info.get("courseCategoryName", ""),
                "学分": course_info.get("unit", 0),
                "考试类型": course_info.get("examTypeName", ""),
                "选课状态": course_info.get("selectCourseStatusName", ""),
                "上课安排": []
            }
            for t in course_info.get("timeAndPlaceList", []):
                base["上课安排"].append({
                    "星期": t.get("classDay"),
                    "节次": f"{t.get('classSessions')}-{t.get('classSessions') + t.get('continuingSession') - 1}",
                    "周次描述": t.get("weekDescription", ""),
                    "校区": t.get("campusName", ""),
                    "教学楼": t.get("teachingBuildingName", ""),
                    "教室": t.get("classroomName", "")
                })
            result.append(base)
    return result


def print_schedule(course_list):
    print("=" * 100)
    print("📚 河北大学 本学期课表")
    print("=" * 100)
    week_map = {1: "周一", 2: "周二", 3: "周三", 4: "周四", 5: "周五", 6: "周六", 7: "周日"}
    for idx, c in enumerate(course_list, 1):
        print(f"\n【{idx}】{c['课程名称']} | {c['课程性质']} | 学分:{c['学分']} | {c['考试类型']}")
        print(f"\t授课教师: {c['教师']}")
        if not c["上课安排"]:
            print("\t⚠️ 该课程无排课地点（实训/军事技能等）")
            continue
        for arr in c["上课安排"]:
            wd = week_map.get(arr["星期"], f"星期{arr['星期']}")
            print(f"\t▸ {wd} 第{arr['节次']}节 | {arr['周次描述']} | {arr['校区']} {arr['教学楼']} {arr['教室']}")
    print("\n" + "=" * 100)


def save_json(course_list, filename="schedule_out.json"):
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(course_list, f, ensure_ascii=False, indent=2)
    print(f"\n✅ 解析完成,结构化课表保存至 {filename}")


if __name__ == "__main__":
    try:
        raw_json = get_schedule_by_playwright()
        courses = parse_courses(raw_json)
        print_schedule(courses)
        save_json(courses)
    except Exception as e:
        import traceback
        print(f"\n❌程序异常: {e}")
        traceback.print_exc()
