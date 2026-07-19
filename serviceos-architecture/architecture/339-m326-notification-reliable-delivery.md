---
title: M326 NOTIFICATION 可靠投递闭环
status: Implemented
milestone: M326
lastUpdated: 2026-07-19
relatedMilestones: [M307, M323, M325]
---

# M326 NOTIFICATION 可靠投递闭环

## 目标

领域事件自动订阅冻结 Bundle `NOTIFICATION`，经 `NotificationRuntime.resolveAndDispatch`
派发后，将 Intent / Delivery / Attempt 持久化；LocalReference `SENT` 记本地 ACK，
`UNKNOWN`/`FAILED` 保留人工接管标记。

## 范围与非目标

- 范围：
  - Flyway `V124`：`cfg_notification_intent` / `cfg_notification_delivery` / `cfg_notification_attempt`
  - `task.created` / `task.completed` → `TaskNotificationEventHandler`
  - `NotificationEventDispatchService`：Inbox + RoleGrant 收件人 + Runtime + 持久化 + 审计
  - 幂等：Inbox `configuration.notification.task-event.v1`；Intent `(tenant, eventId, policyKey)`；
    Delivery `idempotency_key`
  - LocalReference `SENT`/`SENT_REPLAY` → `acknowledged_at`（本地 ACK）
- 明确不做：
  - 真实短信/邮件/Push 供应商 Adapter
  - Admin 投递工作台 / 人工重发
  - 模板渲染引擎
  - PRICING 落账
  - OpenAPI 变更
  - 业务重试 Task 时钟（ADR-014 完整分层；本切片 LocalReference 同事务发送）

## 模块边界

- Handler 在 `task`（避免 `configuration ↔ task` 循环依赖）
- Intent/Delivery/Attempt 与 Inbox 同事务在 `configuration`
- 禁止跨模块写内部表：task 只调用 `configuration::api`

## 已实现

- 事件自动订阅 + 持久化投递闭环 + PostgreSQL IT

## 明确未实现

- 真实供应商、Admin UNKNOWN/Replay 工作台、PRICING 主链路、网络 I/O 移出事务

## 验证命令

```bash
bash scripts/agent-verify.sh it NotificationReliableDeliveryPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,DefaultNotificationRuntimeTest
```
