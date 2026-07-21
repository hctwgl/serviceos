---
title: M432 Admin 工单目录当前阶段列验收矩阵
version: 0.1.0
status: Implemented
milestone: M432
lastUpdated: 2026-07-21
---

# M432 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | ACTIVE 任务存在 | 目录/详情 `currentStageCode` = 任务 stageCode | WorkOrderQueryPostgresIT |
| A2 | 无 ACTIVE 任务 | `currentStageCode` = null；UI「未提供」 | PostgresIT + 前端回退 |
| A3 | 授权不变 | 仍需 workOrder.read；无新 capability | 既有范围门禁 |
| A4 | Admin 展示 | 阶段列显示中文标签（如「勘测」） | Playwright |
| A5 | 契约 | OpenAPI 1.0.94；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
