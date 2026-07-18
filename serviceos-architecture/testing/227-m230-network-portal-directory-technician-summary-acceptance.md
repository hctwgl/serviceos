---
title: M230 Network Portal 目录页师傅服务端摘要验收矩阵
status: Implemented
milestone: M230
lastUpdated: 2026-07-17
---

# M230 Network Portal 目录页师傅服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M230-01 | NETWORK technician.readOwnNetwork + 目录命中师傅 | work-orders/tasks 页 `technicians` 含摘要 | pass（PostgresIT） |
| M230-02 | 缺 technician.readOwnNetwork | JSON 省略 `technicians` | pass（PostgresIT） |
| M230-03 | 有能力但 items 无命中师傅 | `technicians=[]` | pass（PostgresIT） |
| M230-04 | 他网点师傅不计入 | 仅本页 technicianId 命中 | pass（PostgresIT） |
| M230-05 | Admin Web 展示/省略与深链 | E2E | pass（E2E） |
| M230-06 | OpenAPI 1.0.10；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
