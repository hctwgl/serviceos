---
title: M47 整改 Task 验收
version: 0.1.0
status: Accepted
---

# M47 整改 Task 验收

| ID | 级别 | 断言 | 证据 |
|---|---|---|---|
| M47-TSK-001 | P0 | REJECTED 同事务创建 evidence.correction Task 并写入 correctionTaskId | `CorrectionCasePostgresIT` |
| M47-TSK-002 | P0 | 同一 CorrectionCase 幂等不重复建 Task | `CorrectionCasePostgresIT` |
| M47-PRJ-001 | P0 | Task RUNNING 时 Case 投影 IN_PROGRESS | `CorrectionCasePostgresIT` |
| M47-API-001 | P1 | OpenAPI 0.22.0 + View 含 correctionTaskId | Contract Validation |
| M47-DEP-001 | P0 | staging 047/49 | staging rehearsal |
