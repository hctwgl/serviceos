---
title: M43 表单+资料双引用完成门禁验收
version: 0.1.0
status: Implemented
---

# M43 表单+资料双引用完成门禁验收

| ID | 优先级 | 验收项 | 自动化证据 |
|---|---|---|---|
| M43-DUAL-001 | P0 | 双引用 Task 仅在 form+snapshot 精确匹配时完成 | `DualInputTaskCompletionPostgresIT` |
| M43-DUAL-002 | P0 | 缺少/错误/跨 Task 引用拒绝且无完成污染 | `DualInputTaskCompletionPostgresIT` |
| M43-DUAL-003 | P0 | 表单-only / 资料-only 既有语义保持 | Form/Evidence 既有 IT + 双引用 IT 说明 |
| M43-TX-001 | P0 | 失败回滚幂等/Task/Outbox；重放稳定 | `DualInputTaskCompletionPostgresIT` |
| M43-API-001 | P1 | OpenAPI 0.18.0 + task.completed@v2 inputVersionRefs | Contract Validation |
| M43-DEP-001 | P0 | staging 正向迁移至 043/45 | staging rehearsal |
