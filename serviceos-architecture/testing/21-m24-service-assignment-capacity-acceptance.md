---
title: M24 ServiceAssignment 与容量权威验收矩阵
version: 0.1.0
status: Proposed
---

# M24 ServiceAssignment 与容量权威验收矩阵

| 场景 | 优先级 | 输入/故障 | 预期证据 |
|---|---:|---|---|
| M24-CAP-001 | P0 | 配置容量并重放 | 首次响应冻结，计数器不重复创建 |
| M24-CAP-002 | P0 | 容量已满或版本陈旧 | 409，Assignment/Reservation 不落半状态 |
| M24-PREP-001 | P0 | 初派或改派 prepare | PENDING Assignment、HELD Reservation、PENDING saga 原子提交 |
| M24-ACT-001 | P0 | Task 已准备后激活 | 旧责任 ENDED/容量 RELEASED，新责任 ACTIVE/容量 CONFIRMED |
| M24-ACT-002 | P0 | authority 与 fence 证明 | 激活责任固化 ID、版本与策略版本 |
| M24-ABT-001 | P0 | SERVICE_SWITCHED 前 abort | 新责任 FAILED、HELD 释放、旧 ACTIVE 保留 |
| M24-TX-001 | P0 | activated Outbox 写入失败 | Assignment/Reservation/Counter/Saga/审计/幂等整体回滚 |
| M24-CONTRACT-001 | P0 | 六个事件版本 | 文件名、eventType、Schema 与有效样本治理通过 |
| M24-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`024`、applied=`26`、重复 migrate=0 |

自动化主证据为 `DispatchServiceAssignmentPostgresIT`、`EventSchemaGovernanceTest`、模块架构测试与完整
Maven `clean verify`。跨模块 Inbox saga 不在本矩阵内，不能据此宣称完整改派闭环。
