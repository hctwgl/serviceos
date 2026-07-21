---
title: M445 Admin 工单目录即将超时筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M445
lastUpdated: 2026-07-21
---

# M445 Admin 工单目录即将超时筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | slaRisk=NEAR | 仅返回 30 分钟内将到期的 RUNNING | `listFiltersByNearSlaRiskWithinThirtyMinutes` |
| A2 | 窗口外 RUNNING / BREACHED | 不命中 NEAR | 同上 |
| A3 | 非法枚举 | IllegalArgumentException | `listFiltersBySlaRisk` |
| A4 | Admin 选项 | 「即将超时」可见 | Playwright |

产品状态：`READY_FOR_REVIEW`。
