# HBUschedule

河北大学课程表 Android App：在自己手机上登录教务系统，把课表变成能看的界面和桌面小组件。

不需要服务器，不需要注册账号，**课程数据只存在学生自己的设备里**。

## 当前状态

开发中。已完成的部分可以直接构建安装，但请注意：

| | 状态 |
| --- | --- |
| 周课表界面、课程详情、学期周次换算 | 已完成 |
| 教务数据解析、`.ics` 日历导出 | 已完成，有单元测试 |
| **App 内登录教务、拉自己的真实课表** | **未做**，现在装完看到的是脱敏样例数据 |
| 系统日历写入、上课提醒、桌面小组件 | 未做 |

所以下载试用可以，但别期待能看到自己的课表。

## 为什么值得做

看课表要先过 WebVPN 登录，手机上体验很差，而且教务里的周次是「1-9周」这种相对说法，没法直接变成日历提醒。

本项目解决的是最后一步：**把课表搬到手机上**。登录链路全部在学生设备上完成，团队不持有任何人账号。

## 技术要点

- Kotlin + Jetpack Compose，`minSdk 26`（Android 8.0 起），`compileSdk 37`
- 零后端：CAS 统一认证换票 → 网瑞达 WebVPN 代理前缀 → 青果 URP 教务接口
- 周次以接口返回的 `timeAndPlaceList[].classWeek` **24 位 0/1 位图**为准，不解析中文周次文案
- 节次共 11 节：上午 1-4、下午 5-8、晚上 9-11，第 1 节 08:20-09:05
- `.ics` 导出把周次拆成步长 1 或 2 的连续段，一段一条 `RRULE`，双周课 `INTERVAL=2`

## 目录结构

```
app/                    Android 应用（Kotlin + Compose）
  src/main/java/cn/hbu/schedule/
    data/               接口 JSON → 课表模型
    model/              课表模型、节次作息、学期周次换算
    export/             .ics 日历导出
    ui/                 周课表界面与课程详情
  src/test/             单元测试，测试数据用 docs 下的真实样例
docs/
  sample_raw_schedule.json   真实教务响应（已脱敏）
tools/
  capture-schedule.mjs       零依赖命令行抓取脚本，用于验证接口
.github/workflows/           CI：跑测试并产出 debug APK
kbzq.py                 最早的 Playwright 抓取脚本（项目起点）
```

## 构建

CI 会自动构建：推送后在仓库的 **Actions** 页面找到最近一次成功的 run，页面底部 Artifacts 里下载 `hbu-schedule-debug`（需要登录 GitHub，产物保留 90 天）。

本地构建需要 Gradle 和 Android SDK（仓库里不放 `gradle-wrapper.jar`）：

```bash
gradle :app:test          # 跑单元测试
gradle :app:assembleDebug # 产出 app/build/outputs/apk/debug/app-debug.apk
```

装到手机：把 apk 传到手机打开安装，会提示「未知来源」，属正常现象（debug 签名）。

## 抓取脚本

想验证接口是否还能用，不需要装任何依赖：

```bash
node tools/capture-schedule.mjs --probe   # 只测连通性和登录页
node tools/capture-schedule.mjs           # 学号密码登录，导出 schedule_raw.json
```

输出文件含真实个人课表，已在 `.gitignore` 里，不要提交、不要外传。

## 数据与声明

数据来源为河北大学本科生教务系统。登录在学生自己的设备上完成，课程数据只保存在该设备本地，本项目不设任何服务器、不上传任何个人课表。如认为存在侵权，请联系仓库维护者，将第一时间处理。

## 致谢与参考

- [sasaju/NormalSchedule（河大课表 Android）](https://github.com/sasaju/NormalSchedule) —— 前辈为河北大学写的课程表 App，Apache-2.0，曾上架小米/华为/OPPO/VIVO 等应用商店。本项目参考了它的课表界面结构与交互设计（固定时间轴 + 横向滚动课表格子 + 点击课程弹详情）。截至当前只参考结构与思路，尚未复制其代码；一旦移植具体实现，会在对应文件保留原始版权声明，并在 `THIRD_PARTY_NOTICES.md` 中登记来源与改动。
- [XingHeYuZhuan/shiguangschedule（拾光课程表）](https://github.com/XingHeYuZhuan/shiguangschedule) 及其[适配脚本仓库](https://github.com/XingHeYuZhuan/shiguang_warehouse) —— Apache-2.0，其「时间轴 + 详情弹窗 + 桌面小组件」的形态是本项目界面的参照之一。
- 本仓库的教务抓取起点为 `kbzq.py`。

## License

MIT
