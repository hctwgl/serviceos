---
title: M226 Network Portal 工作区运营异常摘要验收矩阵
status: Implemented
milestone: M226
lastUpdated: 2026-07-17
---

# M226 Network Portal 工作区运营异常摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M226-01 | NETWORK operations.exception.read + ACTIVE 任务异常 | exceptions 含摘要且 allowedActions=[] | pass（PostgresIT） |
| M226-02 | 缺 operations.exception.read | JSON 省略 exceptions | pass（PostgresIT） |
| M226-03 | 他网点任务异常不计入 | 仅 ACTIVE taskIds | pass（PostgresIT） |
| M226-04 | 有能力但无异常 | exceptions=[] | pass（PostgresIT） |
| M226-05 | Admin Web 展示/省略与深链 | E2E | pass（E2E） |
| M226-06 | OpenAPI 1.0.6；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
