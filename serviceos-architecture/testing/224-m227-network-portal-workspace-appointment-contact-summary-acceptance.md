---
title: M227 Network Portal 工作区预约/联系服务端摘要验收矩阵
status: Implemented
milestone: M227
lastUpdated: 2026-07-17
---

# M227 Network Portal 工作区预约/联系服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M227-01 | NETWORK manageAppointment + ACTIVE 任务预约/联系 | appointments/contactAttempts 含摘要且无 PII | pass（PostgresIT） |
| M227-02 | 缺 networkPortal.manageAppointment | JSON 同时省略两属性 | pass（PostgresIT） |
| M227-03 | 他网点任务/他网点 assignedNetworkId 不计入 | 仅 ACTIVE taskIds + network 过滤 | pass（PostgresIT） |
| M227-04 | 有能力但无数据 | 两属性均为 [] | pass（PostgresIT） |
| M227-05 | Admin Web 展示/省略与深链 | E2E | pass（E2E） |
| M227-06 | OpenAPI 1.0.7；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
