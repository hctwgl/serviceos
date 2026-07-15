---
title: M54 车企回执影响对象权威校验验收矩阵
version: 1.0.0
status: Implemented
---

# M54 车企回执影响对象权威校验验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M54-TGT-001 | P0 | 精确目标属于 ReviewCase Snapshot | 回执保存强类型 slot/item/revision 三元组，EXTERNAL 决定与驳回协调 Task 正常创建 |
| M54-TGT-002 | P0 | revision 属于另一个 Snapshot | `VALIDATION_FAILED`，Case 保持 OPEN，无回执、决定或协调 Task |
| M54-TGT-003 | P0 | 合法 revision 搭配错误 slot/item | 三元组整体校验失败，不按单个 ID 猜测归属 |
| M54-TGT-004 | P0 | 同一 revision 重复声明 | 整次命令失败关闭，不静默去重 |
| M54-TGT-005 | P0 | targetType/必填 UUID 非法或目标超过 100 | HTTP/应用契约拒绝，不产生副作用 |
| M54-TX-001 | P0 | 任一目标失败 | 目标校验先于状态迁移；ReviewDecision、Task、审计、Outbox 全部不写入 |
| M54-IDEM-001 | P0 | 合法回执按命令键或 inboundEnvelopeId 重放 | 返回首次不可变回执，不重复决定、Task、审计或 Outbox |
| M54-SEC-001 | P0 | USER、匿名或缺 capability | 403/401，不能探测或写入跨租户 Snapshot |
| M54-CON-001 | P0 | OpenAPI 与客户端生成 | 0.27.0 生成强类型 `ExternalReviewAffectedTarget`，禁止 arbitrary object |
| M54-CON-002 | P0 | 对 0.26.0 执行兼容检查 | 门禁准确报告四个新增必填字段；该破坏性变更已有 2026-07-15 明确批准，不得用兼容兜底消除报告 |
| M54-MOD-001 | P0 | 模块边界 | 校验只使用 evidence 内公开模型/Repository 端口，Spring Modulith 校验通过 |

## 自动化证据映射

| 场景 | 证据入口 |
|---|---|
| TGT-001、IDEM-001 | `ReviewCasePostgresIT.recordsExternalReceiptAndOpensCoordinationTaskOnReject` |
| TGT-002～004、TX-001 | `ReviewCasePostgresIT.rejectsExternalTargetsOutsideAuthoritativeReviewSnapshotWithoutSideEffects` |
| TGT-005、CON-001 | OpenAPI 3.1 schema、`ContractValidationTest`、`GeneratedClientContractTest` |
| CON-002 | `check-contract-compatibility.sh 55e9aed`，预期且实际报告 `affectedTargets.items` 四个新增必填属性 |
| SEC-001 | `ReviewCasePostgresIT` SERVICE/USER 路径、`ExternalReviewReceiptControllerSecurityTest` |
| MOD-001 | `ArchitectureTest` / `ApplicationModules.verify()` |

M54 没有数据库结构变化，因此不新增 Flyway；PostgreSQL 18 Testcontainers 仍用于证明不可变
SnapshotMember 权威读取与事务无副作用。完整 Connector 入站、CLIENT Case 自动创建、外部批次权威
登记和其他 targetType 不属于本矩阵。
