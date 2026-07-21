---
title: M435 Admin 工单目录独立 updatedAt 验收矩阵
version: 0.1.0
status: Implemented
milestone: M435
lastUpdated: 2026-07-21
---

# M435 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 新建工单 | `updatedAt == receivedAt` | WorkOrderQueryPostgresIT |
| A2 | activate 后 | `updatedAt > receivedAt`；列表/详情一致 | PostgresIT |
| A3 | MVC JSON | `items[].updatedAt` 必返 | WorkOrderControllerSecurityTest |
| A4 | Admin 展示 | 「更新时间」显示 fixture `updatedAt` 日期部分 | Playwright |
| A5 | 契约/迁移 | OpenAPI 1.0.97；Flyway V146 | 契约 diff + IT version assert |

产品状态：`READY_FOR_REVIEW`。
