---
title: M442 Admin 工单目录按 SLA 风险筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M442
lastUpdated: 2026-07-21
---

# M442 Admin 工单目录按 SLA 风险筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | slaRisk=OPEN | 返回 RUNNING 与 BREACHED 工单 | `listFiltersBySlaRisk` |
| A2 | slaRisk=BREACHED | 仅返回 BREACHED | 同上 |
| A3 | 非法枚举 | 400 / IllegalArgumentException | 同上 |
| A4 | Admin 筛选 | 「SLA 风险」Select 可用 | Playwright + 截图 |

产品状态：`READY_FOR_REVIEW`。
