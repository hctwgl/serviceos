---
title: M46 files 作废联动验收
version: 0.1.0
status: Implemented
---

# M46 files 作废联动验收

| ID | 级别 | 断言 | 证据 |
|---|---|---|---|
| M46-FIL-001 | P0 | Evidence 联动使 AVAILABLE→INVALIDATED | `EvidenceRevisionInvalidationPostgresIT` |
| M46-LNK-001 | P0 | Evidence invalidate 同事务作废关联 StoredFile | `EvidenceRevisionInvalidationPostgresIT` |
| M46-DL-001 | P0 | INVALIDATED 不可授权下载 | `EvidenceRevisionInvalidationPostgresIT` |
| M46-TX-001 | P0 | 幂等重放稳定 | File/Evidence IT |
| M46-API-001 | P1 | OpenAPI 0.21.0 + 事件 Schema | Contract Validation |
| M46-DEP-001 | P0 | staging 046/48 | staging rehearsal |
