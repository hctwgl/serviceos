---
title: M229 Network Portal 工作区审核案例服务端摘要验收矩阵
status: Implemented
milestone: M229
lastUpdated: 2026-07-17
---

# M229 Network Portal 工作区审核案例服务端摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M229-01 | NETWORK evidence.read + ACTIVE 任务审核 | reviews 含摘要且无 note/decidedBy | pass（PostgresIT） |
| M229-02 | 缺 evidence.read | JSON 省略 reviews（与 corrections 同时） | pass（PostgresIT） |
| M229-03 | 他网点任务审核不计入 | 仅 ACTIVE taskIds | pass（PostgresIT） |
| M229-04 | 有能力但无审核 | reviews=[] | pass（PostgresIT） |
| M229-05 | Admin Web 展示/省略 | E2E | pass（E2E） |
| M229-06 | OpenAPI 1.0.9；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
