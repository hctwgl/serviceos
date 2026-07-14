---
title: M42 EvidenceRevision 作废运行时验收
version: 0.1.0
status: Implemented
---

# M42 EvidenceRevision 作废运行时验收

| ID | 优先级 | 验收项 | 自动化证据 |
|---|---|---|---|
| M42-EVD-001 | P0 | VALIDATED→INVALIDATED 写一次 reason/actor/time | `EvidenceRevisionInvalidationPostgresIT` |
| M42-EVD-002 | P0 | 非 VALIDATED 拒绝且无投影/审计/Outbox 污染 | `EvidenceRevisionInvalidationPostgresIT` |
| M42-EVD-003 | P0 | 槽位投影按计入状态刷新；INVALIDATED 不计入 | `EvidenceRevisionInvalidationPostgresIT` |
| M42-EVD-004 | P0 | 已存在 Snapshot 成员不被改写；作废后不可再选入新 Snapshot | `EvidenceRevisionInvalidationPostgresIT` |
| M42-TX-001 | P0 | 幂等重放返回同一 Revision；键复用冲突 | `EvidenceRevisionInvalidationPostgresIT` |
| M42-SEC-001 | P0 | 缺 capability / 匿名拒绝 | MVC Security + PostgreSQL IT |
| M42-API-001 | P1 | OpenAPI 0.17.0 + `evidence.revision-invalidated@v1` | Contract Validation |
| M42-DEP-001 | P0 | staging 正向迁移至 042/44 | staging rehearsal |
