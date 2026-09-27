"""河北课表数据模型：把教务接口原始 JSON 归一化，供界面、日历导出、手机端复用。

原始结构（由 kbzq.py 抓到的 callback 响应）:
    {"xkxx": [{"<课程编号>": {"courseName": ..., "timeAndPlaceList": [...]}}]}
"""

import re

WEEK_CN = {1: "周一", 2: "周二", 3: "周三", 4: "周四", 5: "周五", 6: "周六", 7: "周日"}

# 河北大学作息，节次 -> (开始, 结束)。改这里即可适配调休/新课表。
SESSION_TIMES = {
    1: ("08:00", "08:45"), 2: ("08:55", "09:40"),
    3: ("10:00", "10:45"), 4: ("10:55", "11:40"),
    5: ("14:30", "15:15"), 6: ("15:25", "16:10"),
    7: ("16:20", "17:05"), 8: ("17:15", "18:00"),
    9: ("19:00", "19:45"), 10: ("19:55", "20:40"),
    11: ("20:50", "21:35"),
}


class Meeting:
    """一次上课安排：某天某几节、第几周上、在哪上。"""

    def __init__(self, day, first_session, last_session, weeks=None,
                 odd_week=False, even_week=False, week_description="",
                 campus="", building="", room=""):
        self.day = day
        self.first_session = first_session
        self.last_session = last_session
        # weeks 为 None 表示整个学期每周都上
        self.weeks = weeks
        self.odd_week = odd_week
        self.even_week = even_week
        self.week_description = week_description
        self.campus = campus
        self.building = building
        self.room = room

    @property
    def location(self):
        return " ".join(p for p in (self.campus, self.building, self.room) if p)

    @property
    def session_label(self):
        if self.last_session == self.first_session:
            return f"第{self.first_session}节"
        return f"第{self.first_session}-{self.last_session}节"

    def weeks_in(self, total_weeks):
        """展开成实际上课的周次列表（1 起）。"""
        if self.weeks:
            return sorted(self.weeks)
        if self.odd_week:
            return [w for w in range(1, total_weeks + 1, 2)]
        if self.even_week:
            return [w for w in range(2, total_weeks + 1, 2)]
        return list(range(1, total_weeks + 1))

    def to_dict(self):
        return {
            "day": self.day,
            "first_session": self.first_session,
            "last_session": self.last_session,
            "session_label": self.session_label,
            "weeks": self.weeks,
            "odd_week": self.odd_week,
            "even_week": self.even_week,
            "week_description": self.week_description,
            "campus": self.campus,
            "building": self.building,
            "room": self.room,
        }


class Course:
    def __init__(self, name, teacher="", category="", property="", credits=0,
                 exam_type="", enroll_status="", code="", meetings=None):
        self.name = name
        self.teacher = teacher
        self.category = category
        self.property = property
        self.credits = credits
        self.exam_type = exam_type
        self.enroll_status = enroll_status
        self.code = code
        self.meetings = meetings or []

    @property
    def place_text(self):
        return "；".join(m.location for m in self.meetings if m.location)

    def to_dict(self):
        return {
            "name": self.name,
            "teacher": self.teacher,
            "category": self.category,
            "property": self.property,
            "credits": self.credits,
            "exam_type": self.exam_type,
            "enroll_status": self.enroll_status,
            "code": self.code,
            "meetings": [m.to_dict() for m in self.meetings],
        }


_RANGE_RE = re.compile(r"(\d+)\s*[-–—~至]\s*(\d+)")
_NUMS_RE = re.compile(r"\d+")


def parse_week_description(text):
    """把 "1-8周"、"单周"、"1,3,5周" 之类解析成 (weeks, odd_week, even_week)。

    解析不出具体周次时返回 (None, ...)，即按全学期处理，
    只在识别到单/双周标记时收窄范围。
    """
    text = (text or "").strip()
    if not text:
        return None, False, False

    odd = any(k in text for k in ("单周", "(单)", "（单）", "单"))
    even = any(k in text for k in ("双周", "偶", "(双)", "（双）"))

    m = _RANGE_RE.search(text)
    if m:
        start, end = int(m.group(1)), int(m.group(2))
        step = 2 if (odd or even) else 1
        return list(range(start, end + 1, step)), odd, even

    nums = [int(n) for n in _NUMS_RE.findall(text)]
    if nums:
        return nums, odd, even
    return None, odd, even


def parse_session(value):
    """节次可能是 "3"、"3-4"、"03"、3，统一成 (起始节, 结束节)。"""
    text = str(value or "").strip()
    if "-" in text or "~" in text or "－" in text:
        parts = re.split(r"[-~－]", text)
        nums = [int(p) for p in parts if p.strip().isdigit()]
        if len(nums) == 2:
            return min(nums), max(nums)
    if text.isdigit():
        n = int(text)
        return n, n
    return 1, 1


def _as_int(value, default=0):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def normalize(raw):
    """原始接口 JSON -> [Course]。"""
    if isinstance(raw, dict) and "courses" in raw:
        raw = raw["courses"]

    courses = []
    for item in raw.get("xkxx", []) if isinstance(raw, dict) else []:
        if not isinstance(item, dict):
            continue
        for code, info in item.items():
            if not isinstance(info, dict):
                continue
            meetings = []
            for tp in info.get("timeAndPlaceList", []) or []:
                first, last = parse_session(tp.get("classSessions"))
                cont = _as_int(tp.get("continuingSession"), 1)
                if cont > 1:
                    last = max(last, first + cont - 1)
                weeks, odd, even = parse_week_description(tp.get("weekDescription"))
                meetings.append(Meeting(
                    day=_as_int(tp.get("classDay"), 1),
                    first_session=first,
                    last_session=last,
                    weeks=weeks,
                    odd_week=odd,
                    even_week=even,
                    week_description=str(tp.get("weekDescription", "") or "").strip(),
                    campus=str(tp.get("campusName", "") or "").strip(),
                    building=str(tp.get("teachingBuildingName", "") or "").strip(),
                    room=str(tp.get("classroomName", "") or "").strip(),
                ))
            courses.append(Course(
                name=str(info.get("courseName", "") or "").strip(),
                teacher=str(info.get("attendClassTeacher", "") or "").strip(),
                category=str(info.get("courseCategoryName", "") or "").strip(),
                property=str(info.get("coursePropertiesName", "") or "").strip(),
                credits=info.get("unit", 0),
                exam_type=str(info.get("examTypeName", "") or "").strip(),
                enroll_status=str(info.get("selectCourseStatusName", "") or "").strip(),
                code=str(code),
                meetings=meetings,
            ))
    return courses


def load_courses(path):
    """读取 kbzq.py 旧版输出(schedule_out.json)或新版 dict，返回 [Course]。"""
    import json
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict) and "courses" in data:
        return courses_from_dicts(data["courses"])
    if isinstance(data, dict) and "xkxx" in data:
        return normalize(data)
    return courses_from_dicts(_upgrade_legacy_list(data))


def courses_from_dicts(items):
    courses = []
    for c in items:
        meetings = [Meeting(**{k: v for k, v in m.items() if k != "session_label"})
                    for m in c.get("meetings", [])]
        meta = {k: v for k, v in c.items() if k != "meetings"}
        courses.append(Course(**meta, meetings=meetings))
    return courses


def _upgrade_legacy_list(data):
    """兼容 kbzq.py 之前的中文键输出格式。"""
    out = []
    for c in data:
        meetings = []
        for a in c.get("上课安排", []):
            first, last = parse_session(a.get("节次"))
            weeks, odd, even = parse_week_description(a.get("周次描述"))
            day = a.get("星期")
            if isinstance(day, str):
                day = next((k for k, v in WEEK_CN.items() if v == day), 1)
            meetings.append(Meeting(
                day=_as_int(day, 1), first_session=first, last_session=last,
                weeks=weeks, odd_week=odd, even_week=even,
                week_description=a.get("周次描述", ""),
                campus=a.get("校区", ""), building=a.get("教学楼", ""),
                room=a.get("教室", ""),
            ).to_dict())
        out.append({
            "name": c.get("课程名称", ""),
            "teacher": c.get("教师", ""),
            "property": c.get("课程性质", ""),
            "category": c.get("课程类别", ""),
            "credits": c.get("学分", 0),
            "exam_type": c.get("考试类型", ""),
            "enroll_status": c.get("选课状态", ""),
            "meetings": meetings,
        })
    return out


def build_week_grid(courses, total_weeks=20):
    """返回 {(day, session): [（课程, 会议)]}，用于画周课表格子。"""
    grid = {}
    for course in courses:
        for m in course.meetings:
            for s in range(m.first_session, m.last_session + 1):
                grid.setdefault((m.day, s), []).append((course, m))
    return grid
