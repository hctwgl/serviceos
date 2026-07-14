---
title: M21 TaskAssignment 候选与责任运行时
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M21 TaskAssignment 候选与责任运行时

## 1. 目标与边界

M21 将 `TaskAssignment` 落为 Task 模块拥有的责任唯一事实源。授权操作者通过
`POST /api/v1/tasks/{taskId}:assign-candidates` 为 READY HUMAN Task 冻结 1～100 个 USER 候选；
只有 ACTIVE 候选可 claim，claim 同事务建立唯一 ACTIVE RESPONSIBLE。当前责任人可在 start 前通过
`POST /api/v1/tasks/{taskId}:release` 释放，Task 回到 READY，其他原候选可再次领取。

候选批次保存来源类型、稳定来源 ID、候选集合、Task 版本、操作者和时间。替换候选不会覆盖历史，而是
撤销旧 ACTIVE CANDIDATE 并创建新批次。Task 完成时，候选与责任有效期在同一事务闭合。

## 2. 不变量

- 候选配置只接受 HUMAN + READY，并检查 `If-Match`；
- 候选列表规范化、去重和排序后参与请求摘要，幂等重放返回首次冻结批次；
- claim 同时要求 `task.claim` 实时 RoleGrant 和 ACTIVE USER 候选，二者不能互相替代；
- 每个 Task 同一时刻最多一个 ACTIVE RESPONSIBLE，由 PostgreSQL 部分唯一索引兜底；
- start/complete/release 同时核对 `claimed_by` 与 ACTIVE RESPONSIBLE；
- release 只允许 CLAIMED→READY，必须保存稳定 reasonCode，并关闭当前责任有效期；
- Task、Assignment、审计、幂等和 Outbox 位于同一事务，任何事件写入失败全部回滚。

## 3. 契约与迁移

V021 新增候选批次和 TaskAssignment 表、`task.assign`/`task.release` capability。事件新增
`task.assigned@v1` 与 `task.released@v1`；OpenAPI Core 客户端版本为 `0.4.0`。当前 Flyway
版本为 `021`，包含 repeatable 共 `23` 条，staging Gate 同步验证 `021/23`。

## 4. 尚未证明

M21 不实现 ASSIGNEE_POLICY 内容解析、ROLE/ORGANIZATION/NETWORK 候选、ServiceAssignment、容量预占、
师傅改派激活 saga、TaskExecutionGuard、SLA、个人待办查询和离线撤权。派单模块不得直接写本表；后续必须
通过可靠握手准备/激活责任，并在切换窗口阻止 Task 执行。
