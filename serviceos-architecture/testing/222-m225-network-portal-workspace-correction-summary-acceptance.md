---
title: M225 Network Portal 工作区整改摘要验收矩阵
status: Implemented
milestone: M225
lastUpdated: 2026-07-17
---

# M225 Network Portal 工作区整改摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M225-01 | NETWORK evidence.read + ACTIVE 任务整改 | corrections 含摘要字段且无 waiveNote | pass（PostgresIT） |
| M225-02 | 缺 evidence.read | JSON 省略 corrections | pass（PostgresIT） |
| M225-03 | 他网点任务整改不计入 | 仅 ACTIVE taskIds | pass（PostgresIT） |
| M225-04 | 有能力但无整改 | corrections=[] | pass（PostgresIT） |
| M225-05 | Admin Web 展示/省略与深链 | E2E | pass（E2E） |
| M225-06 | OpenAPI 1.0.5；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
