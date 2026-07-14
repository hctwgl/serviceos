---
title: M38 EvidenceItem 与不可变 EvidenceRevision 验收
version: 0.1.0
status: Implemented
---

# M38 EvidenceItem 与不可变 EvidenceRevision 验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M38-INT-001 | P0 | evidence 编排 files Begin/Finalize，不读 files 内部表 | `ArchitectureTest`、`EvidenceItemRevisionPostgresIT` |
| M38-EVD-001 | P0 | Finalize 成功后创建 Item + 不可变 Revision（STORED） | `EvidenceItemRevisionPostgresIT` |
| M38-EVD-002 | P0 | 相同 finalizeCommandId / upload session 重试不重复创建 | `EvidenceItemRevisionPostgresIT` |
| M38-EVD-003 | P0 | 并发 Item 创建不突破 maxCount | `EvidenceItemRevisionPostgresIT` |
| M38-EVD-004 | P0 | 扫描 CLEAN→VALIDATING、恶意→QUARANTINED，并刷新槽位投影 | `EvidenceItemRevisionPostgresIT`、`EvidenceSlotStatusProjectorTest` |
| M38-TX-001 | P0 | Revision/投影/审计/Outbox/幂等同事务；文件 I/O 在事务外 | `EvidenceItemRevisionPostgresIT` |
| M38-DATA-001 | P0 | Revision 核心事实与 Item 身份触发器不可变 | `EvidenceItemRevisionPostgresIT`、V039 |
| M38-SEC-001 | P0 | 匿名拒绝；JWT tenant/Project Scope；改派与 Guard 拒绝 | `EvidenceItemControllerSecurityTest`、PostgreSQL IT |
| M38-CAP-001 | P0 | CaptureMetadata 规范化；onBehalfOf 失败关闭 | `CaptureMetadataValidatorTest` |
| M38-API-001 | P1 | OpenAPI 0.13.0 描述上传与查询契约 | Contract Validation |
| M38-EVT-001 | P1 | revision-created / validation-state-changed Schema 可治理 | Event Schema Governance |
| M38-DEP-001 | P0 | staging 正向迁移至 039/41 | staging rehearsal |

本矩阵仅证明 EvidenceSlot → 安全文件 → EvidenceItem/Revision → 投影查询的最小闭环。条件槽位、
OCR/审核/整改、Snapshot 与完成门禁仍由 M3 EVD 后续里程碑验收。
