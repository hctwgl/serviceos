---
title: M20 人工工作流 Task 命令运行时
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
  - decisions/ADR-017-workflow-runtime-selection.md
---

# M20 人工工作流 Task 命令运行时

## 1. 目标与边界

M20 将工作流中的 `USER_TASK`、`REVIEW_TASK` 和 `MANUAL_INTERVENTION` 落为可操作的人工任务，
实现 `READY → CLAIMED → RUNNING → COMPLETED`。公开入口为
`POST /api/v1/tasks/{taskId}:claim|start|complete`，全部要求 JWT、`Idempotency-Key` 和
双引号包裹的 `If-Match` 聚合版本。

M20 不把自动 Worker 的 `CLAIMED` 租约字段当作人工责任事实。V020 增加 `claimed_by`、
`claimed_at`、`started_at`、结果引用/摘要与冻结命令响应表；自动执行仍独占
`claim_owner/claim_until/current_attempt_id`。

## 2. 事务与安全不变量

- tenant/actor 只从受信 `CurrentPrincipal` 获取，正文和自定义头不能覆盖；
- `task.claim`、`task.start`、`task.complete` 必须同时通过 token 声明与实时 RoleGrant；
- claim 只接受 HUMAN + READY，start/complete 只接受当前领取人；
- 每次变更同时检查 `expectedVersion`，并原子递增 Task version；
- 幂等抢占、Task 状态、冻结响应、授权审计和 Outbox 位于同一事务；
- 相同幂等键/摘要始终返回首次冻结响应，即使 Task 后续已推进；
- 人工完成复用 `task.completed@v1`，因此与自动任务进入同一 Inbox 工作流推进链路。

## 3. 契约与发布门禁

M20 发布 `task.claimed@v1`、`task.started@v1`，继续使用 `task.completed@v1`。OpenAPI Core
客户端版本为 `0.3.0`。Flyway 当前版本为 `020`，包含 repeatable 后共 `22` 条迁移，staging
必须在启动 backend 前验证 `020/22`。

## 4. 尚未证明

M20 尚未提供候选人/责任人 Assignment 聚合、release/block/resolve/cancel、SLA 时钟、离线移动端
同步、动态表单完成条件、网关/并行/等待事件或完整勘安业务资产。这些仍是完整现场履约平台的
后续必需能力，不能用租户级 RoleGrant 替代候选人策略。
