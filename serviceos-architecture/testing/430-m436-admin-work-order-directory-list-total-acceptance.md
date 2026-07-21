---
title: M436 Admin 工单目录列表 total 验收矩阵
version: 0.1.0
status: Implemented
milestone: M436
lastUpdated: 2026-07-21
---

# M436 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 少量匹配 | `totalCount` 精确；`truncatedated=false` | WorkOrderQueryPostgresIT |
| A2 | 分页翻页 | 首页 limit=1 仍返回完整筛选 total | PostgresIT |
| A3 | 超过 100 | `totalCount=100` 且 `truncatedated=true` | PostgresIT |
| A4 | Admin 展示 | 工具栏「共 1 条」 | Playwright |
| A5 | 契约 | OpenAPI 1.0.98；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
