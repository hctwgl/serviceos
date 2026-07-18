---
title: M224 Network Portal 工作台薄 SLA 风险计数验收矩阵
status: Implemented
milestone: M224
lastUpdated: 2026-07-17
---

# M224 Network Portal 工作台薄 SLA 风险计数验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M224-01 | NETWORK sla.read + ACTIVE 任务 RUNNING/BREACHED | slaSummary.open/breached 正确 | pass（PostgresIT） |
| M224-02 | 缺 sla.read | JSON 省略 slaSummary | pass（PostgresIT） |
| M224-03 | 他网点 SLA 不计入 | 仅 ACTIVE taskIds | pass（PostgresIT） |
| M224-04 | 有能力但无实例 | openCount=0 / breachedCount=0 | pass（PostgresIT） |
| M224-05 | Admin Web 展示/省略 | E2E | pass（E2E） |
| M224-06 | OpenAPI 1.0.4；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
