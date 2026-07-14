---
title: M26 切换前可靠终止验收矩阵
version: 0.1.0
status: Proposed
---

# M26 切换前可靠终止验收矩阵

| 场景 | 优先级 | 输入/故障 | 预期证据 |
|---|---:|---|---|
| M26-CHECKPOINT-001 | P0 | TaskAssignmentPrepared | Dispatch 持久保存 guard/PREPARED，saga=`TASK_PREPARED:2` |
| M26-FORWARD-001 | P0 | 无终止命令 | checkpoint 事件推进 SERVICE_SWITCHED，M25 正常闭环仍为 COMPLETED:4 |
| M26-ABORT-001 | P0 | TASK_PREPARED 时受控终止 | 新 ServiceAssignment FAILED、HELD 释放、旧责任保持 ACTIVE |
| M26-TASK-001 | P0 | Task 消费 aborted v2 | PREPARED→ABORTED、guard RELEASED、claimed_by/旧责任不变 |
| M26-ACK-001 | P0 | TaskAssignmentAborted | Dispatch `ABORTING:3→ABORTED:4` 且 completedAt 非空 |
| M26-STALE-001 | P0 | abort 后旧 checkpoint 到达 | Inbox 成功确认，不激活 FAILED_ACTIVATION 责任 |
| M26-TX-001 | P0 | Task aborted Outbox 写失败 | Task 撤销与 Inbox 回滚；源事件恢复后只前向完成一次 |
| M26-AUTH-001 | P0 | 消费时发起人授权撤销 | 消费失败关闭，不解除 guard 或伪造终态 |
| M26-CONTRACT-001 | P0 | 三个新增事件版本 | v1 不可变，Schema/样本/命名治理通过 |
| M26-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`026`、applied=`28`、重复 migrate=0 |

自动化主证据为 `DispatchTaskReassignmentSagaPostgresIT` 的正常、终止和故障恢复场景，配合 M23/M24
PostgreSQL IT、事件契约治理、模块架构测试和完整 Maven `clean verify`。
