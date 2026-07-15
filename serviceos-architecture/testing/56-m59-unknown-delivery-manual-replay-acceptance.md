---
title: M59 UNKNOWN 外部交付人工重发验收矩阵
version: 1.0.0
status: Implemented
---

# M59 UNKNOWN 外部交付人工重发验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M59-CMD-001 | P0 | USER 对 UNKNOWN Delivery 发起重发 | 原子创建 ReplayRequest、Task、Outbox、Audit，返回 202 |
| M59-CMD-002 | P0 | 相同 Idempotency-Key 与相同输入重放 | 返回同一 ReplayRequest，不创建第二 Task |
| M59-CMD-003 | P0 | SERVICE、缺 capability、缺原因/审批、跨 project、陈旧版本或非 UNKNOWN | 401/403/400/409 失败关闭，无重放副作用 |
| M59-DB-001 | P0 | 并发不同命令 | 一个 Delivery 同时最多一个 REQUESTED/EXECUTING ReplayRequest |
| M59-DB-002 | P0 | 直接修改 UNKNOWN Delivery 或 ReplayRequest 授权/终态 | PostgreSQL trigger 拒绝；只有 EXECUTING ReplayRequest 可开启新 Attempt |
| M59-EXE-001 | P0 | 授权重发 | 同一 Delivery/payload digest/external key，新 Attempt 序号递增，旧 UNKNOWN attempt 保留 |
| M59-EXE-002 | P0 | 重发再次 UNKNOWN | ReplayRequest 与 Delivery 均为 UNKNOWN，Task 人工接管，不自动发送第三次 |
| M59-EXE-003 | P0 | 重发明确成功但本地落账首次失败 | Delivery 保持 DELIVERED；Task 只重试 CLIENT Case/Route 落账，HTTP 总次数不增加 |
| M59-SEC-001 | P0 | 查询 Delivery | 仅授权 tenant/project 可见；ReplayRequest 摘要不暴露 payload/response 对象引用或凭据 |
| M59-CON-001 | P0 | OpenAPI、事件 Schema 与客户端 | 0.32.0 可解析、样例有效、客户端可重复生成、兼容门禁通过 |
| M59-MOD-001 | P0 | 模块边界 | integration 只经公开 API/SPI 协作，ArchitectureTest 通过 |
| M59-MIG-001 | P0 | 空库迁移 | PostgreSQL 18 原生镜像应用 61 个迁移并到达 v059 |

## 自动化证据映射

- `ReviewCasePostgresIT#authorizedManualReplayPreservesUnknownAttemptAndFrozenPayload`：
  M59-CMD-001/002、EXE-001/003、SEC-001；
- `ReviewCasePostgresIT#manualReplayThatIsStillUnknownReturnsToManualInterventionWithoutAutomaticThirdSend`：
  M59-EXE-002；
- `ReviewCasePostgresIT#manualReplayFailsClosedForServicePrincipalMissingApprovalAndStaleVersion`：
  M59-CMD-003；
- V059 空库迁移、唯一索引/trigger 与全量 PostgreSQL IT：M59-DB/MIG；
- `OutboundDeliveryControllerSecurityTest`：匿名拒绝及请求体门禁；
- `ContractValidationTest`、`EventSchemaGovernanceTest`、`GeneratedClientContractTest`、
  客户端可重复生成、兼容检查与 `ArchitectureTest`：M59-CON/MOD。

本矩阵不宣称人工标记已送达、放弃、远端查询、批量重放审批、通用 Connector、Portal 或通知中心完成。
