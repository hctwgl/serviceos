---
title: M222 Network Portal 工作区 Visit/表单提交摘要验收矩阵
status: Implemented
milestone: M222
lastUpdated: 2026-07-17
---

# M222 Network Portal 工作区 Visit/表单提交摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M222-01 | NETWORK visit.read + 本网点 ACTIVE 任务 Visit | visits 含摘要且无 GPS/note | pass（PostgresIT） |
| M222-02 | 缺 visit.read | JSON 省略 visits | pass（PostgresIT） |
| M222-03 | 他网点 Visit 不计入 | 过滤 networkId/taskIds | pass（PostgresIT） |
| M222-04 | NETWORK form.read + ACTIVE 任务提交 | formSubmissions 无 values | pass（PostgresIT） |
| M222-05 | 缺 form.read | JSON 省略 formSubmissions | pass（PostgresIT） |
| M222-06 | Admin Web 展示/省略 | E2E | pass（E2E） |
| M222-07 | OpenAPI 1.0.2；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
