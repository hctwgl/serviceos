---
title: M44 ReviewCase / ReviewDecision 验收
version: 0.1.0
status: Implemented
---

# M44 ReviewCase / ReviewDecision 验收

| ID | 优先级 | 验收项 | 自动化证据 |
|---|---|---|---|
| M44-REV-001 | P0 | 可为 TASK_SUBMISSION Snapshot 创建 OPEN ReviewCase | `ReviewCasePostgresIT` |
| M44-REV-002 | P0 | APPROVED/REJECTED 只追加一次；重复裁决失败关闭 | `ReviewCasePostgresIT` |
| M44-REV-003 | P0 | 非 TASK_SUBMISSION / 缺失 Snapshot / 重复创建拒绝 | `ReviewCasePostgresIT` |
| M44-TX-001 | P0 | 幂等重放稳定；失败无 Outbox/审计污染 | `ReviewCasePostgresIT` |
| M44-SEC-001 | P0 | 缺 capability / 匿名拒绝 | MVC Security |
| M44-API-001 | P1 | OpenAPI 0.19.0 + 事件 Schema | Contract Validation |
| M44-DEP-001 | P0 | staging 044/46 | staging rehearsal |
| M44-MOD-001 | P0 | Modulith 边界保持 | `ArchitectureTest` |
