import datetime as dt
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import ics_export
import schedule_model

SAMPLE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      "docs", "sample_raw_schedule.json")
WEEK1 = dt.date(2026, 9, 7)


def load_sample():
    with open(SAMPLE, encoding="utf-8") as f:
        return schedule_model.normalize(json.load(f))


class TestModel(unittest.TestCase):
    def test_normalizes_all_courses(self):
        courses = load_sample()
        self.assertEqual(len(courses), 5)
        by_name = {c.name: c for c in courses}
        self.assertIn("高等数学（上）", by_name)
        self.assertEqual(by_name["高等数学（上）"].teacher, "王建国")

    def test_session_range_from_continuing(self):
        maths = next(c for c in load_sample() if c.name == "高等数学（上）")
        first = maths.meetings[0]
        self.assertEqual((first.first_session, first.last_session), (1, 2))
        self.assertEqual(first.session_label, "第1-2节")

    def test_no_room_course(self):
        mil = next(c for c in load_sample() if c.name == "军事技能训练")
        self.assertEqual(mil.meetings, [])

    def test_week_description_parsing(self):
        self.assertEqual(schedule_model.parse_week_description("1-18周"),
                         (list(range(1, 19)), False, False))
        weeks, odd, even = schedule_model.parse_week_description("1-16周(单)")
        self.assertEqual(weeks, [1, 3, 5, 7, 9, 11, 13, 15])
        self.assertTrue(odd)
        self.assertFalse(even)
        self.assertEqual(schedule_model.parse_week_description("3-15周"),
                         ([3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], False, False))
        self.assertEqual(schedule_model.parse_week_description(""), (None, False, False))

    def test_odd_even_expand_over_total_weeks(self):
        m = schedule_model.Meeting(day=1, first_session=1, last_session=1,
                                   odd_week=True)
        self.assertEqual(m.weeks_in(9)[-1], 9)
        self.assertEqual(len(m.weeks_in(9)), 5)

    def test_roundtrip_through_dict(self):
        courses = load_sample()
        rebuilt = schedule_model.courses_from_dicts([c.to_dict() for c in courses])
        self.assertEqual(rebuilt[0].meetings[0].location, courses[0].meetings[0].location)

    def test_legacy_chinese_keys_upgrade(self):
        legacy = [{"课程名称": "旧格式课", "教师": "某人", "上课安排":
                   [{"星期": "周三", "节次": "3-4", "周次描述": "1-8周",
                     "校区": "校区", "教学楼": "楼", "教室": "101"}]}]
        course = schedule_model.courses_from_dicts(
            schedule_model._upgrade_legacy_list(legacy))[0]
        self.assertEqual(course.name, "旧格式课")
        self.assertEqual(course.meetings[0].day, 3)
        self.assertEqual(course.meetings[0].last_session, 4)
        self.assertEqual(course.meetings[0].location, "校区 楼 101")

    def test_load_courses_from_file(self):
        self.assertEqual(len(schedule_model.load_courses(SAMPLE)), 5)


class TestIcs(unittest.TestCase):
    def test_basic_shape(self):
        text = ics_export.to_ics(load_sample(), WEEK1, total_weeks=18)
        self.assertTrue(text.startswith("BEGIN:VCALENDAR\r\n"))
        self.assertIn("END:VCALENDAR\r\n", text)
        self.assertNotIn("\n\r", text.replace("\r\n", ""))

    def test_events_are_balanced(self):
        lines = ics_export.build_events(load_sample(),
                                        ics_export.semester_week_dates(WEEK1, 18))
        count = lambda prefix: sum(1 for ln in lines if ln.startswith(prefix))
        self.assertEqual(count("BEGIN:VEVENT"), count("UID:"))
        self.assertEqual(count("BEGIN:VEVENT"), count("END:VEVENT"))
        self.assertEqual(count("DTSTART:"), count("DTEND:"))
        self.assertEqual(count("BEGIN:VALARM"), count("END:VALARM"))

    def test_odd_week_uses_interval_2(self):
        text = ics_export.to_ics(load_sample(), WEEK1, total_weeks=18)
        self.assertIn("INTERVAL=2", text)

    def test_event_dates_anchor_to_week1(self):
        courses = [c for c in load_sample() if c.name == "高等数学（上）"]
        dates = ics_export.semester_week_dates(WEEK1, 18)
        lines = ics_export.build_events(courses, dates)
        dtstarts = [ln for ln in lines if ln.startswith("DTSTART:")]
        # 周一第1节 -> 9月7日 08:00
        self.assertEqual(dtstarts[0], "DTSTART:20260907T080000")

    def test_no_meeting_course_produces_no_event(self):
        courses = [c for c in load_sample() if c.name == "军事技能训练"]
        self.assertEqual(ics_export.build_events(courses,
                                                ics_export.semester_week_dates(WEEK1, 18)), [])

    def test_empty_export_raises(self):
        with self.assertRaises(ValueError):
            ics_export.to_ics([], WEEK1, total_weeks=18)

    def test_long_lines_are_folded(self):
        text = ics_export.to_ics(load_sample(), WEEK1, total_weeks=18)
        for ln in text.split("\r\n"):
            self.assertLessEqual(len(ln.encode("utf-8")), 75)

    def test_alarm_present(self):
        text = ics_export.to_ics(load_sample(), WEEK1, total_weeks=18)
        self.assertIn("TRIGGER:-PT15M", text)

    def test_fold_reassembles(self):
        long_line = "DESCRIPTION:" + "很长的课程说明" * 20
        parts = ics_export._folds(long_line)
        rejoined = parts[0] + "".join(p[1:] for p in parts[1:])
        self.assertEqual(rejoined, long_line)


if __name__ == "__main__":
    unittest.main(verbosity=2)
