---
title: M434 Admin 工单目录 SLA 风险旁载验收矩阵
version: 0.1.0
status: Implemented
milestone: M434
lastUpdated: 2026-07-21
---

# M434 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 有 sla.read + 开放风险 | `slaRiskSummaries` 含 open/breached | WorkOrderQueryPostgresIT |
| A2 | 无 sla.read | 属性省略（null） | PostgresIT |
| A3 | 有能力无开放风险 | `[]`；UI「暂无」 | 实现语义 |
| A4 | Admin 展示 | `开放 1 / 超时 0` | Playwright |
| A5 | 契约 | OpenAPI 1.0.96；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
