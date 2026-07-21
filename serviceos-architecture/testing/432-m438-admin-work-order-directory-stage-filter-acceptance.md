---
title: M438 Admin 工单目录按阶段筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M438
lastUpdated: 2026-07-21
---

# M438 Admin 工单目录按阶段筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | `currentStageCode=SURVEY` | 仅返回当前阶段为 SURVEY 的授权工单 | `WorkOrderQueryPostgresIT#listFiltersByCurrentStageCode` |
| A2 | `currentStageCode=INSTALLATION` | 仅返回 INSTALLATION | 同上 |
| A3 | 无匹配阶段 | 空页 + totalCount=0 | 同上 |
| A4 | 非法码（小写） | 400 / IllegalArgumentException | 同上 |
| A5 | MVC 透传 query | Controller 将 `currentStageCode` 传入 Query | `WorkOrderControllerSecurityTest` |
| A6 | Admin 更多筛选 | 「当前阶段」Select 可见且可交互 | Playwright `work-order-stage-filter` + 截图 |

产品状态：`READY_FOR_REVIEW`（未宣称 PRODUCT_ACCEPTED / VISUAL_APPROVED）。
