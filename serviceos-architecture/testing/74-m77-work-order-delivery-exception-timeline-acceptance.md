---
title: M77 工单外发交付与异常闭环时间线事件合并验收矩阵
status: Implemented
milestone: M77
---

# M77 工单外发交付与异常闭环时间线事件合并验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M77-01 | outbound-delivery-created | 直接写入工单投影，category=DELIVERY，outcome=CREATED |
| M77-02 | exception.resolved@v2 | 经 TaskTimelineContextQuery；outcome=resolutionCode |
| M77-03 | 非工单 Task 异常 | Inbox 完成但不投影 |
| M77-04 | 信息最小化 | 不保存 orderCode、digest、payload |
| M77-05 | 工程门禁 | V075、OpenAPI 0.48.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest |

不验收 acknowledged/recovered/replay、exception acknowledged、checkpoint/重建和 Portal。
