---
title: M25 Dispatch 与 Task 改派 Saga 验收矩阵
version: 0.1.0
status: Proposed
---

# M25 Dispatch 与 Task 改派 Saga 验收矩阵

| 场景 | 优先级 | 输入/故障 | 预期证据 |
|---|---:|---|---|
| M25-E2E-001 | P0 | ACTIVE 师傅 A 改派到 B | 四段事件全部消费，双侧责任对齐，saga `COMPLETED:4` |
| M25-GUARD-001 | P0 | pending v2 被 Task 消费 | guard ACTIVE、B PREPARED，A 仍为当前责任 |
| M25-SWITCH-001 | P0 | TaskAssignmentPrepared 被 Dispatch 消费 | A ServiceAssignment ENDED/B ACTIVE，容量同步切换 |
| M25-ACT-001 | P0 | ServiceAssignmentActivated 被 Task 消费 | A TaskAssignment REVOKED/B ACTIVE，claimed_by=B，guard RELEASED |
| M25-INBOX-001 | P0 | 四个消息重复或发布结果未知 | 每个 consumer/event 只保存一个 SUCCEEDED，领域事实不重复 |
| M25-TX-001 | P0 | activated v2 Outbox 写入失败 | Dispatch 与 Inbox 回滚到 PENDING，Task PREPARED 可向前重试 |
| M25-AUTH-001 | P0 | 消费时发起人授权已撤销 | 消费失败关闭，不切换责任或解除 guard |
| M25-CONTRACT-001 | P0 | pending/activated v2 | v1 不可变，v2 文件名、版本、eventType 与样本治理通过 |
| M25-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`025`、applied=`27`、重复 migrate=0 |

自动化主证据为 `DispatchTaskReassignmentSagaPostgresIT`、M23/M24 PostgreSQL IT、事件契约治理、
模块架构测试和完整 Maven `clean verify`。
