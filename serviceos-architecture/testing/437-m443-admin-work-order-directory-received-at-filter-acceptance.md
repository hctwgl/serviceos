---
title: M443 Admin 工单目录按创建时间筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M443
lastUpdated: 2026-07-21
---

# M443 Admin 工单目录按创建时间筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | receivedFrom/To 闭区间 | 仅返回 Asia/Shanghai 自然日内 `receivedAt` 命中工单 | `listFiltersByReceivedAt` |
| A2 | 单日区间 | 仅当日 | 同上 |
| A3 | to < from | IllegalArgumentException | 同上 |
| A4 | Controller 绑定 | date 查询参数进入 WorkOrderQuery | `WorkOrderControllerSecurityTest` |
| A5 | Admin 筛选 | 「创建时间」RangePicker 可用 | Playwright + 截图 |

产品状态：`READY_FOR_REVIEW`。
