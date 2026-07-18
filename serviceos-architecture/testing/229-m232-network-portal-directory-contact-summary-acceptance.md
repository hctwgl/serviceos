---
title: M232 Network Portal 目录页联系尝试服务端摘要验收矩阵
status: Implemented
milestone: M232
lastUpdated: 2026-07-17
---

# M232 Network Portal 目录页联系尝试服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M232-01 | NETWORK manageAppointment + 目录任务联系 | work-orders/tasks 页 `contactAttempts` 含摘要 | pass（PostgresIT） |
| M232-02 | 缺 manageAppointment | JSON 省略 `contactAttempts` | pass（PostgresIT） |
| M232-03 | 有能力但无联系 | `contactAttempts=[]` | pass（PostgresIT） |
| M232-04 | 他网点任务联系不计入 | 仅本页 taskIds | pass（PostgresIT） |
| M232-05 | Admin Web 最近联系列展示/省略 | E2E | pass（E2E） |
| M232-06 | OpenAPI 1.0.12；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
