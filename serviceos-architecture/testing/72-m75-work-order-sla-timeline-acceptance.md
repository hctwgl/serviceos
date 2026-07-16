---
title: M75 工单 SLA 时间线事件合并验收矩阵
status: Implemented
milestone: M75
---

# M75 工单 SLA 时间线事件合并验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M75-01 | sla.started | 直接写入工单投影，category=SLA，outcome=STARTED，resourceCode=slaRef |
| M75-02 | sla.breached / sla.met | 仅通过 TaskTimelineContextQuery 解析 workOrder；outcome=BREACHED 或 MET/MET_LATE |
| M75-03 | 非工单 Task SLA | Inbox 成功完成但不新增时间线条目 |
| M75-04 | 事件重复 / digest 变化 | 重放不新增；同 eventId 不同 digest 失败关闭 |
| M75-05 | Project/身份错配 | 事务回滚，投影与 Inbox 均不留下半成品 |
| M75-06 | 信息最小化 | 不保存/返回 digest、elapsedSeconds、payload 或自由文本 |
| M75-07 | 工程门禁 | V073、OpenAPI 0.46.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 通过 |

本矩阵不验收 Evidence/Review、Delivery、异常、试算/结算时间线，亦不验收 BUSINESS 日历、
暂停/预警、checkpoint、重建作业和 Portal。
