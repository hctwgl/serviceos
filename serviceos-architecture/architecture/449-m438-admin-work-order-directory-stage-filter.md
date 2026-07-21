---
title: M438 Admin 工单目录按阶段筛选
version: 0.1.0
status: Implemented
milestone: M438
lastUpdated: 2026-07-21
relatedMilestones: [M432, M437]
openapiVersion: "1.0.100"
---

# M438 Admin 工单目录按阶段筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「按阶段筛选」：目录查询支持与 `currentStageCode` 列同口径的精确阶段过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.100**：可选 `currentStageCode` query（`^[A-Z][A-Z0-9_]*$`） |
| Backend | task SPI `findWorkOrderIdsByCurrentStageCode`（DISTINCT ON 最早 ACTIVE 任务）→ 授权 SQL `id IN (...)`；写入 cursor filterDigest |
| Admin Web | 更多筛选「当前阶段」Select（常用阶段中文标签）；路由/SavedView 水合 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径与 M432 列一致：最早 ACTIVE 任务的 `stageCode`
- 无 ACTIVE 任务的工单不会命中任何阶段筛选
- workorder 不直接读 `tsk_task`；经 SPI + Spring 装配
- 无 Flyway、无新 capability

## 4. 明确未实现

- 网点/师傅/SLA/创建时间筛选
- 即将超时窗口、超过 100 的精确全量 COUNT（网点/师傅列已由 M439 关闭）
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
