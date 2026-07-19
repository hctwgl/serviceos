---
title: M326 NOTIFICATION 可靠投递验收矩阵
status: Implemented
milestone: M326
lastUpdated: 2026-07-19
---

# M326 NOTIFICATION 可靠投递验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M326-01 | task.created + 冻结 NOTIFICATION + 角色收件人 | Intent COMPLETED；Delivery SENT 且 acknowledged_at 非空；Attempt SENT | `NotificationReliableDeliveryPostgresIT` |
| M326-02 | 角色池为空 | Intent PARTIAL + requires_manual；无 Delivery | 同上 |
| M326-03 | Inbox 幂等 | consumer `configuration.notification.task-event.v1` SUCCEEDED | 同上 |
| M326-04 | 审计 | `NOTIFICATION_RUNTIME_DISPATCHED` | 同上 |
| M326-05 | 模块边界 | configuration 不依赖 task；ArchitectureTest | ArchitectureTest |
| M326-06 | 既有 Runtime | LocalReference / 幂等行为不变 | `DefaultNotificationRuntimeTest` |

## 明确不验收

- 真实 SMS/EMAIL Adapter、Admin 工作台、PRICING、OpenAPI、跨进程重试时钟
