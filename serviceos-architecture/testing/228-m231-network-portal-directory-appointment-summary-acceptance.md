---
title: M231 Network Portal 目录页预约服务端摘要验收矩阵
status: Implemented
milestone: M231
lastUpdated: 2026-07-17
---

# M231 Network Portal 目录页预约服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M231-01 | NETWORK manageAppointment + 目录任务预约 | work-orders/tasks 页 `appointments` 含摘要 | pass（PostgresIT） |
| M231-02 | 缺 manageAppointment | JSON 省略 `appointments` | pass（PostgresIT） |
| M231-03 | 有能力但无预约 | `appointments=[]` | pass（PostgresIT） |
| M231-04 | 他网点 assignedNetworkId / 他网点任务不计入 | 仅本页 taskIds + 可信 networkId | pass（PostgresIT） |
| M231-05 | Admin Web 预约窗口列展示/省略 | E2E | pass（E2E） |
| M231-06 | OpenAPI 1.0.11；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
