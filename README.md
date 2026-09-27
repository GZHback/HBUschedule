# HBUschedule
河北大学课程表：适用于河北大学本科生的课程表(开发中)

## 致谢与参考

- [sasaju/NormalSchedule（河大课表 Android）](https://github.com/sasaju/NormalSchedule) —— 前辈为河北大学写的课程表 App，Apache-2.0，曾上架小米/华为/OPPO/VIVO 等应用商店。本项目参考了它的课表界面结构与交互设计（固定时间轴 + 横向滚动课表格子 + 点击课程弹详情）。截至当前只参考结构与思路，尚未复制其代码；一旦移植具体实现，会在对应文件保留原始版权声明，并在 `THIRD_PARTY_NOTICES.md` 中登记来源与改动。
- [XingHeYuZhuan/shiguangschedule（拾光课程表）](https://github.com/XingHeYuZhuan/shiguangschedule) 及其[适配脚本仓库](https://github.com/XingHeYuZhuan/shiguang_warehouse) —— Apache-2.0，其「时间轴 + 详情弹窗 + 桌面小组件」的形态是本项目界面的参照之一。
- 本仓库的教务抓取起点为 `kbzq.py`。

## 数据与声明

数据来源为河北大学本科生教务系统。登录在学生自己的设备上完成，课程数据只保存在该设备本地，本项目不设任何服务器、不上传任何个人课表。如认为存在侵权，请联系仓库维护者，将第一时间处理。
