---
title: M440 Admin 工单目录按网点筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M440
lastUpdated: 2026-07-21
---

# M440 Admin 工单目录按网点筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | ACTIVE NETWORK 命中 | 仅返回该网点工单 | `listFiltersByCurrentNetworkId` |
| A2 | 无匹配网点 | 空页 + totalCount=0 | 同上 |
| A3 | MVC 契约 | query `currentNetworkId` 传入查询 | `WorkOrderControllerSecurityTest` |
| A4 | Admin 筛选 | 「服务网点」Select 可用 | Playwright + 截图 |

产品状态：`READY_FOR_REVIEW`。
