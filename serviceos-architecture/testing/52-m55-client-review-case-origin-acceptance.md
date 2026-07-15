---
title: M55 CLIENT ReviewCase 来源与回执批次门禁验收矩阵
version: 1.0.0
status: Implemented
---

# M55 CLIENT ReviewCase 来源与回执批次门禁验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M55-ORG-001 | P0 | 已通过 INTERNAL Case 创建 CLIENT Case | 同一 Snapshot/digest 被冻结，source、外部提交、批次、mapping、policy 完整且不可改写 |
| M55-ORG-002 | P0 | source 仍 OPEN/REJECTED 或 source origin=CLIENT | `REVIEW_CASE_STATE_CONFLICT`，不创建 Case、审计或 Outbox |
| M55-ORG-003 | P0 | 重复 idempotency key | 返回首次 CLIENT Case，不重复写入 |
| M55-ORG-004 | P0 | 重复 externalSubmissionRef 或同 origin OPEN Snapshot | 失败关闭，不静默复用其他命令结果 |
| M55-SEC-001 | P0 | USER、匿名、缺 capability 或跨 project scope | 401/403，不泄露 source 或创建 CLIENT Case |
| M55-DEC-001 | P0 | INTERNAL decide/force/reopen 命令操作 CLIENT Case | 拒绝；CLIENT Case 只能由 ExternalReviewReceipt 裁决 |
| M55-RCP-001 | P0 | 回执命中 CLIENT Case 冻结批次与 mapping | 继续执行 M49/M54 回执、决定、协调 Task、审计和 Outbox 事务链 |
| M55-RCP-002 | P0 | 回执指向 INTERNAL Case或批次/mapping 不匹配 | 失败关闭，无 EXTERNAL 决定、回执或协调 Task |
| M55-DB-001 | P0 | V054 空库迁移 | PostgreSQL 18 原生镜像完成 56 个迁移并到达 v054，约束和唯一索引生效 |
| M55-CON-001 | P0 | OpenAPI/事件/客户端 | 0.28.0 与 `client-review-case-created@v1` 可解析、可生成、可重复，兼容门禁通过 |
| M55-MOD-001 | P0 | 模块边界 | 仍在 evidence 公开 API/应用/端口/适配器分层内，Modulith 校验通过 |

## 自动化证据映射

| 场景 | 证据入口 |
|---|---|
| ORG-001～004、DEC-001、RCP-001～002、DB-001 | `ReviewCasePostgresIT` + V054 + PostgreSQL 18 Testcontainers |
| SEC-001 | `ReviewCasePostgresIT`、`ReviewCaseControllerSecurityTest`、`ExternalReviewReceiptControllerSecurityTest` |
| CON-001 | `ContractValidationTest`、`EventSchemaGovernanceTest`、`GeneratedClientContractTest`、兼容与生成复现脚本 |
| MOD-001 | `ArchitectureTest` / `ApplicationModules.verify()` |

完整 Connector、InboundEnvelope/CanonicalMessage、OutboundDelivery、跨模块批次登记和 Portal 不属于
本矩阵；没有这些证据时不得把 M55 外推为完整车企集成闭环。
