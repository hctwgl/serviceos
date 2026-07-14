---
title: M27 ServiceAssignment 激活超时对账验收矩阵
version: 0.1.0
status: Proposed
---

# M27 ServiceAssignment 激活超时对账验收矩阵

| 场景 | 优先级 | 输入/故障 | 预期证据 |
|---|---:|---|---|
| M27-DEADLINE-001 | P0 | 创建及推进激活 saga | 每个非终态拥有新 deadline；终态 deadline=NULL |
| M27-SCAN-001 | P0 | TASK_PREPARED 超过 deadline | 精确 stage/version occurrence 与 timeout Outbox 各一条 |
| M27-GUARD-001 | P0 | 超时被检测 | saga 版本不变、Task guard ACTIVE、HELD 容量不释放 |
| M27-OPS-001 | P0 | timeout 事件投递 | DISPATCH/P1 OPEN 异常与 HUMAN handling Task 同事务创建 |
| M27-DEDUP-001 | P0 | 同阶段重复扫描 | 第二次扫描无新 occurrence/event/Task |
| M27-AGG-001 | P0 | 同 saga 后续阶段再次超时 | occurrenceCount 增加，复用原 handling Task |
| M27-TX-001 | P0 | timeout Outbox 插入失败 | occurrence、lastError 与事件全部回滚，恢复后可重试 |
| M27-CONTRACT-001 | P0 | timed-out v1 事件 | Schema、样本与事件命名治理通过 |
| M27-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`027`、applied=`29`、重复 migrate=0 |

自动化主证据为 `DispatchTaskReassignmentSagaPostgresIT` 的到期、guard 保持、异常接管与事务回滚场景，
以及 `TaskExecutionPostgresIT` 的多阶段 occurrence 聚合。配合架构测试、事件契约治理和完整 Maven
`clean verify`。
