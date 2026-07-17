---
title: M221 Network Portal 工作区薄 SLA 摘要验收矩阵
status: Implemented
milestone: M221
lastUpdated: 2026-07-17
---

# M221 Network Portal 工作区薄 SLA 摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M221-01 | NETWORK sla.read + RUNNING/BREACHED | slaSummary 计数正确 | pass（PostgresIT） |
| M221-02 | 缺 sla.read | JSON 省略 slaSummary | pass（PostgresIT） |
| M221-03 | 他网点任务实例不计入 | 过滤 taskIds | pass（PostgresIT） |
| M221-04 | 无 ACTIVE 责任工单 | ACCESS_DENIED | pass（既有 + IT） |
| M221-05 | Admin Web 展示/省略 | E2E | pass（E2E） |
| M221-06 | OpenAPI 1.0.1；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
