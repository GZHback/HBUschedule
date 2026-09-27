"""课表 -> iCalendar(.ics) 导出，纯标准库实现。

教务的周次是"第几周上"，日历事件要的是具体日期，所以必须知道
第 1 周周一是几号；单双周、断开的周次区间用 RRULE 的
INTERVAL/COUNT 表达，连续段不同步时拆成多条 VEVENT。
"""

import datetime as dt
import hashlib
import re

from schedule_model import SESSION_TIMES, WEEK_CN


def semester_week_dates(week1_monday, total_weeks):
    """{周次: 该周周一日期}"""
    return {w: week1_monday + dt.timedelta(weeks=w - 1)
            for w in range(1, total_weeks + 1)}


def _runs(weeks, step):
    """把周次列表切成"等差连续段"，供 RRULE 用。

    [1,2,3,7,8] step=1 -> [[1,2,3],[7,8]]
    [1,3,5,9]    step=2 -> [[1,3,5],[9]]
    """
    if not weeks:
        return []
    weeks = sorted(weeks)
    out, cur = [], [weeks[0]]
    for w in weeks[1:]:
        if w - cur[-1] == step:
            cur.append(w)
        else:
            out.append(cur)
            cur = [w]
    out.append(cur)
    return out


def _folds(line):
    """RFC5545 要求每行 <=75 字节，续行必须以一个空格开头。"""
    if len(line.encode("utf-8")) <= 75:
        return [line]
    parts, buf = [], ""
    for ch in line:
        if buf and len((buf + ch).encode("utf-8")) > 75:
            parts.append(buf)
            buf = " " + ch
        else:
            buf += ch
    if buf:
        parts.append(buf)
    return parts


def _escape(text):
    return (text or "").replace("\\", "\\\\").replace(";", "\\;") \
        .replace(",", "\\,").replace("\n", "\\n")


def _uid(*parts):
    digest = hashlib.md5("|".join(str(p) for p in parts).encode("utf-8")).hexdigest()
    return f"{digest}@hbuschedule"


def _stamp(value):
    return value.strftime("%Y%m%dT%H%M%S")


_BYDAY = {1: "MO", 2: "TU", 3: "WE", 4: "TH", 5: "FR", 6: "SA", 7: "SU"}


def build_events(courses, week_dates, warn=None):
    """返回 VEVENT 行列表（不含 VCALENDAR 包装）。"""
    total_weeks = max(week_dates) if week_dates else 0
    lines = []
    for course in courses:
        for m in course.meetings:
            weeks = m.weeks_in(total_weeks)
            weeks = [w for w in weeks if w in week_dates]
            if not weeks:
                continue
            times = SESSION_TIMES.get(m.first_session)
            if not times:
                if warn is not None:
                    warn.append(f"{course.name}: 第{m.first_session}节不在作息表内，已跳过")
                continue
            start_clock = times[0]
            end_clock = SESSION_TIMES.get(m.last_session, times)[1]
            step = weeks[1] - weeks[0] if len(weeks) > 1 else 1

            desc = "\n".join(filter(None, (
                f"教师：{course.teacher}" if course.teacher else "",
                f"{WEEK_CN.get(m.day, '')} {m.session_label}",
                f"周次：{m.week_description or '全学期'}",
                f"学分：{course.credits}" if course.credits else "",
            )))

            for run in _runs(weeks, step):
                day = week_dates[run[0]] + dt.timedelta(days=m.day - 1)
                start = dt.datetime.combine(
                    day, dt.datetime.strptime(start_clock, "%H:%M").time())
                end = dt.datetime.combine(
                    day, dt.datetime.strptime(end_clock, "%H:%M").time())
                interval = step if len(run) > 1 else 1

                lines.append("BEGIN:VEVENT")
                _add(lines, f"UID:{_uid(course.code, m.day, m.first_session, run[0], run[-1])}")
                _add(lines, f"DTSTAMP:{_stamp(dt.datetime.now())}")
                _add(lines, f"DTSTART:{_stamp(start)}")
                _add(lines, f"DTEND:{_stamp(end)}")
                if len(run) > 1:
                    _add(lines, "RRULE:FREQ=WEEKLY;"
                                f"BYDAY={_BYDAY.get(m.day, 'MO')};"
                                f"INTERVAL={interval};COUNT={len(run)}")
                _add(lines, f"SUMMARY:{_escape(course.name)}")
                if m.location:
                    _add(lines, f"LOCATION:{_escape(m.location)}")
                _add(lines, f"DESCRIPTION:{_escape(desc)}")
                lines.append("BEGIN:VALARM")
                lines.append("ACTION:DISPLAY")
                _add(lines, f"DESCRIPTION:{_escape(course.name)} 快上课了")
                lines.append("TRIGGER:-PT15M")
                lines.append("END:VALARM")
                lines.append("END:VEVENT")
    return lines


def _add(dst, line):
    dst.extend(_folds(line))


def to_ics(courses, week1_monday, total_weeks=20, prodid="-//HBUschedule//CN", warn=None):
    """生成完整 .ics 文本，导入系统日历即可。"""
    body = build_events(courses, semester_week_dates(week1_monday, total_weeks), warn=warn)
    if not body:
        raise ValueError("没有可导出的课程安排，请检查学期日期或课表数据")

    raw = []
    _add(raw, "BEGIN:VCALENDAR")
    _add(raw, "VERSION:2.0")
    _add(raw, f"PRODID:{prodid}")
    _add(raw, "CALSCALE:GREGORIAN")
    _add(raw, "METHOD:PUBLISH")
    raw.extend(body)
    _add(raw, "END:VCALENDAR")
    return "\r\n".join(raw) + "\r\n"
