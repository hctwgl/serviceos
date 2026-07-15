---
title: M45 CorrectionCase 验收
version: 0.1.0
status: Implemented
---

# M45 CorrectionCase 验收

| ID | 级别 | 断言 | 证据 |
|---|---|---|---|
| M45-COR-001 | P0 | REJECTED 同事务创建 OPEN CorrectionCase | `CorrectionCasePostgresIT` |
| M45-COR-002 | P0 | 同一 ReviewDecision 不重复创建 | `CorrectionCasePostgresIT` |
| M45-COR-003 | P0 | resubmit 追加轮次并进入 RESUBMITTED | `CorrectionCasePostgresIT` |
| M45-COR-004 | P0 | close 仅 RESUBMITTED→CLOSED；非法状态失败关闭 | `CorrectionCasePostgresIT` |
| M45-TX-001 | P0 | 幂等重放稳定；失败无 Outbox/审计污染 | `CorrectionCasePostgresIT` |
| M45-SEC-001 | P0 | 匿名/缺 capability 拒绝 | MVC Security |
| M45-API-001 | P1 | OpenAPI 0.20.0 + 事件 Schema | Contract Validation |
| M45-DEP-001 | P0 | staging 045/47 | staging rehearsal |
| M45-MOD-001 | P0 | Modulith 边界保持 | `ArchitectureTest` |
