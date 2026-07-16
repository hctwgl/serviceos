---
title: M74 工单现场履约时间线事件合并验收矩阵
status: Implemented
milestone: M74
---

# M74 工单现场履约时间线事件合并验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M74-01 | ContactAttempt / Appointment / Visit 已发布事件 | 写入同一工单投影，category/resource/eventType 与来源一致 |
| M74-02 | 与 M73 核心事件并存 | 同一工单按 occurredAt + entryId 稳定混排，不覆盖既有条目 |
| M74-03 | 事件重复 | Inbox 重放不新增条目，同 eventId 不同 digest 失败关闭 |
| M74-04 | 身份错配 | Project、aggregate/resource、tenant 或 occurredAt 不一致时事务回滚 |
| M74-05 | 信息最小化 | 不保存/返回 contactedPartyRef、noShowPartyRef、note、GPS、evidenceRefs、payload |
| M74-06 | 稳定 outcome | propose/confirm 用 status；reschedule 用 RESCHEDULED；cancel/no-show 用 reasonCode；visit check-out/interrupt 用 resultCode/exceptionCode |
| M74-07 | 实时授权 | 仍复用 workOrder.read；撤权 403 + 拒绝审计；跨 tenant 404 |
| M74-08 | HTTP 契约 | Core OpenAPI 扩展 category/eventType/resourceType 枚举；401、ETag、correlation、UNKNOWN freshness |
| M74-09 | 工程门禁 | V072、Core OpenAPI 0.45.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 通过 |

本矩阵不验收 Evidence/Review、Delivery、SLA、异常、试算/结算时间线，亦不验收投影重建作业、
Broker checkpoint、搜索、导出和 Portal。
