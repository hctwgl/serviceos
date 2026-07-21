---
title: M437 Admin 工单目录按区域筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M437
lastUpdated: 2026-07-21
---

# M437 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | districtCode 命中 | 仅返回该区县工单 | WorkOrderQueryPostgresIT |
| A2 | provinceCode 命中 | 仅返回该省工单 | PostgresIT |
| A3 | 无匹配 | 空页；totalCount=0 | PostgresIT |
| A4 | MVC 传参 | Controller 转发 districtCode | WorkOrderControllerSecurityTest |
| A5 | Admin UI | 更多筛选区域 Select 可用 | Playwright |
| A6 | 契约 | OpenAPI 1.0.99；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
