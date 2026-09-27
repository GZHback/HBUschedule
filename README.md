# HBUschedule

河北大学课程表工具：把教务系统的课表抓到本地，用周课表界面查看，并一键导出 `.ics` 导入手机/电脑系统日历。

不需要服务器，不需要注册账号，数据只存在你自己的电脑上。

## 为什么需要它

教务系统看课表要先过 WebVPN 扫码，手机上体验更差，而且没法把课表变成日历提醒。
本工具解决的是最后一步：**把课表搬进你的系统日历，上课前 15 分钟自动提醒**。

## 快速开始

```bash
pip install -r requirements.txt      # 只有抓取需要 playwright；查看和导出零依赖
python3 kbzq.py                      # 弹出浏览器 -> 扫码登录 -> 自动抓课表
python3 app.py                       # 打开本地课表界面，可导出 .ics
```

没有装 Playwright、或者不想跑抓取，也可以直接点界面里的「载入 JSON」，
选一份教务接口返回的 JSON 文件即可。

## 一个必须手动设置的参数

教务数据里的周次是「第 1-18 周」这种相对说法，而日历事件必须是具体日期。
所以第一次使用要在「学期设置」里填 **第 1 周周一是几号** 和 **学期总周数**，
存在 `settings.json`。填错会导致日历里的课整体提前或推后。

## 文件说明

| 文件 | 作用 |
| --- | --- |
| `kbzq.py` | Playwright 扫码登录 WebVPN，劫持 `ajaxStudentSchedule/curr/callback` 响应拿课表 |
| `schedule_model.py` | 原始接口 JSON → 统一课表模型（周次/节次/单双周解析），界面和导出共用 |
| `ics_export.py` | 课表模型 → RFC 5545 `.ics`，单双周用 `RRULE;INTERVAL=2`，断开周次拆多条事件 |
| `app.py` | 本地界面服务（纯标准库），提供周课表视图、学期设置、`.ics` 下载 |
| `web/` | 界面：周课表格子、今天上什么、课程清单 |
| `tests/test_schedule.py` | `python3 -m unittest tests.test_schedule` |

## 数据格式说明

`docs/sample_raw_schedule.json` 是**手工构造的合成样例**，不是真实学生数据。
它按 `kbzq.py` 里已确认的字段（`xkxx` / `courseName` / `timeAndPlaceList` /
`classDay` / `classSessions` / `continuingSession` / `weekDescription`）写成，
用于离线开发和测试。

因此以下几点需要拿真实课表核对，不对就改 `schedule_model.py`：

- `weekDescription` 的真实文案（样例假设了 `1-18周`、`1-16周(单)`、`2-16周(双)`）
- 节次总数与上下课时间（改 `schedule_model.py` 里的 `SESSION_TIMES`）
- 学期第 1 周周一的实际日期

## 已知边界

- 抓取依赖扫码登录，登录态过期后要重新扫，无法后台静默刷新。
- 换校区/调课后要重新抓一次，日历不会自己跟着变。
- 手机实时查看暂未支持：当前形态是「电脑上抓一次 → 导出 .ics → 导入手机日历」。

## License

MIT
