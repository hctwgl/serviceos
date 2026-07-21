---
title: M450 Admin 工单目录异常摘要列验收矩阵
version: 0.1.0
status: Implemented
milestone: M450
lastUpdated: 2026-07-21
---

# M450 Admin 工单目录异常摘要列验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | PROJECT operations.exception.read + OPEN 异常 | 页含 exceptionSummaries，openCount 正确 | `listExposesExceptionSummariesWhenExceptionReadGranted` |
| A2 | 缺 operations.exception.read | 省略 exceptionSummaries（null） | `listOmitsExceptionSummariesWithoutExceptionRead` |
| A3 | MVC | WorkOrderPage 构造含旁载位 | `WorkOrderControllerSecurityTest` |
| A4 | Admin | 列可见「待处理 N」 | Playwright AppShell + 工单中心 |

产品状态：`READY_FOR_REVIEW`。
