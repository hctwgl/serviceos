---
title: M78 工单外发交付确认与恢复时间线事件合并验收矩阵
status: Implemented
milestone: M78
---

# M78 工单外发交付确认与恢复时间线事件合并验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M78-01 | acknowledged / recovered / replay-requested | 经 DeliveryTimelineContextQuery 写入 DELIVERY 投影 |
| M78-02 | Delivery 不存在 | 失败关闭，不留 Inbox/投影半成品 |
| M78-03 | projectId 错配 | 失败关闭 |
| M78-04 | 信息最小化 | 不保存 orderCode、digest、reason、approvalRef |
| M78-05 | Modulith | readmodel → integration::api 通过 ArchitectureTest |
| M78-06 | 工程门禁 | OpenAPI 0.49.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |

不验收 checkpoint/重建、exception.acknowledged、试算/结算和 Portal。
