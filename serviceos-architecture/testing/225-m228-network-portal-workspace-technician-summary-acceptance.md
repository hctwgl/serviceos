---
title: M228 Network Portal 工作区当前师傅服务端摘要验收矩阵
status: Implemented
milestone: M228
lastUpdated: 2026-07-17
---

# M228 Network Portal 工作区当前师傅服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M228-01 | NETWORK technician.readOwnNetwork + 工作区命中师傅 | technicians 含 displayName/membershipId | pass（PostgresIT） |
| M228-02 | 缺 technician.readOwnNetwork | JSON 省略 technicians | pass（PostgresIT） |
| M228-03 | 本网点 ACTIVE 但未命中工作区 technicianId | 不计入 | pass（PostgresIT） |
| M228-04 | 有能力但无命中 | technicians=[] | pass（PostgresIT） |
| M228-05 | Admin Web 展示/省略与深链 | E2E | pass（E2E） |
| M228-06 | OpenAPI 1.0.8；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
