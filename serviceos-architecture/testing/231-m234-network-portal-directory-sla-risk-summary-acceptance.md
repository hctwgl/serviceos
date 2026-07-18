---
title: M234 Network Portal 目录页 SLA 风险服务端摘要验收矩阵
status: Accepted
milestone: M234
lastUpdated: 2026-07-17
---

# M234 验收矩阵（ADR-072）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M234-01 | NETWORK sla.read + 目录 SLA | 页含 `slaRiskSummaries` | pass（PostgresIT） |
| M234-02 | 缺 sla.read | 省略 `slaRiskSummaries` | pass（PostgresIT） |
| M234-03 | 有能力无开放风险 | `slaRiskSummaries=[]` | pass（PostgresIT） |
| M234-04 | 他网点实例不计 | 仅本页 | pass（PostgresIT） |
| M234-05 | Admin Web 列展示/省略 | E2E | pass（E2E） |
| M234-06 | OpenAPI 1.0.14；Flyway 100/102；catalog v16 | 预检 | pass（preflight） |
