---
title: M441 Admin 工单目录按师傅筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M441
lastUpdated: 2026-07-21
---

# M441 Admin 工单目录按师傅筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | ACTIVE TECHNICIAN 命中 | 仅返回该师傅工单 | `listFiltersByCurrentTechnicianId` |
| A2 | 无匹配师傅 | 空页 + totalCount=0 | 同上 |
| A3 | MVC 契约 | query `currentTechnicianId` 传入查询 | `WorkOrderControllerSecurityTest` |
| A4 | Admin 筛选 | 「服务师傅」Select 可用 | Playwright + 截图 |

产品状态：`READY_FOR_REVIEW`。
