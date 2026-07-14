---
title: M40 EvidenceSetSnapshot 验收
version: 0.1.0
status: Implemented
---

# M40 EvidenceSetSnapshot 验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M40-EVD-008 | P0 | S1 冻结后补传不变；新提交创建 S2 | `EvidenceSetSnapshotPostgresIT` |
| M40-EVD-009 | P0 | 跨 Task / 重复 / 非 VALIDATED / 覆盖不足 → 拒绝且零行 | `EvidenceSetSnapshotPostgresIT` |
| M40-DATA-001 | P0 | Snapshot/Member append-only | V041 + PostgreSQL IT |
| M40-TX-001 | P0 | Snapshot/成员/审计/Outbox/幂等同事务 | PostgreSQL IT |
| M40-IDEM-001 | P0 | 相同 Idempotency-Key 重放冻结结果 | PostgreSQL IT |
| M40-SEC-001 | P1 | 匿名拒绝；缺 Capability 拒绝 | `EvidenceSetSnapshotControllerSecurityTest` |
| M40-API-001 | P1 | OpenAPI 0.15.0 | Contract Validation |
| M40-EVT-001 | P1 | `evidence.set-snapshotted@v1` Schema 可治理 | Event Schema Governance |
| M40-DEP-001 | P0 | staging 正向迁移至 041/43 | staging rehearsal |

本矩阵仅证明集合冻结最小闭环。审核、整改、完成门禁仍属后续里程碑。
