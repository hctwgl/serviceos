---
title: M25 Dispatch 与 Task 改派 Inbox Saga
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M25 Dispatch 与 Task 改派 Inbox Saga

## 1. 目标与协议版本

M25 把 M23 Task 责任握手与 M24 Dispatch ServiceAssignment 通过可靠事件接成师傅改派闭环。只有携带
authority/fence 证明的改派使用协议 v2；M24 v1 初派不进入该处理器，避免把“无旧责任”的初派误作改派。

```text
service.assignment.pending-activation@v2
  → task.assignment-prepared@v1
  → service.assignment.activated@v2
  → task.assignment-activated@v1
  → Dispatch saga COMPLETED
```

## 2. 事务和授权边界

- 每个消费者使用独立 consumerName 和 `eventId + payloadDigest` Inbox 去重；
- Inbox begin、领域命令、审计/Outbox、Inbox complete 位于同一本地事务；
- pending 消费者原子创建 Task guard 与 PREPARED responsibility；
- prepared 消费者原子记录 Task 证明、结束旧 ServiceAssignment/容量并激活新责任；
- activated 消费者原子切换 Task 主体、责任与候选并解除 guard；
- Task activated 消费者最后完成 Dispatch saga；
- v2 事件携带原发起人，消费者按当前 RoleGrant 实时复核，不把事件当作永久授权令牌；
- authorityAssignmentId/version 与 fenceDecisionId/policyVersion 在 prepare 时固化，激活事务使用同一证明。

## 3. 迁移、契约与失败恢复

V025 增加激活协议版本和待激活 authority/fence 证明，当前 Flyway 为 `025/27`。新增 pending 与
activated v2 契约，已有 v1 事件保持不可变。四个推进事件在本地发布适配器中属于必须消费事件，缺少
消费者时失败关闭。

消费者事务在 activated Outbox 写入失败时会把 Dispatch 保持 `PENDING:1`，Task 保持 guard +
PREPARED；源 TaskAssignmentPrepared 消息记录失败并可按同一 eventId 重试，恢复后只产生一次结果。

## 4. 尚未证明

M25 尚未实现初派 Task 握手、Network 层双级派单、候选硬过滤与评分、切换前自动 abort 编排、
`SERVICE_SWITCHED` 后授权补偿、saga 超时 OperationalException、SLA 与完整勘安业务链路。
