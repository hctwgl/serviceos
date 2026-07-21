---
title: M432 Admin 工单目录当前阶段列
version: 0.1.0
status: Implemented
milestone: M432
lastUpdated: 2026-07-21
relatedMilestones: [M68, M373, M431]
openapiVersion: "1.0.94"
---

# M432 Admin 工单目录当前阶段列

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「当前阶段」列：目录投影返回与工作区同口径的 `currentStageCode`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.94**：`WorkOrder.currentStageCode` required nullable |
| Backend | `WorkOrderDirectoryStageQuery` SPI（task 实现批量 DISTINCT ON）；目录/详情 enrichment |
| Admin Web | 「当前阶段」列：`statusLabel(code)`；空则「未提供」；Tooltip 保留编码 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径：首个 ACTIVE 任务（READY/PENDING/CLAIMED/RUNNING/RETRY_WAIT/MANUAL_INTERVENTION）的 `stageCode`
- 无 ACTIVE 任务 → `null`；不发明阶段名或 `0`
- workorder 不依赖 task 编译期模块；旁载经 SPI + Spring 装配
- 无 Flyway、无新 capability

## 4. 明确未实现

- 责任人（已由 **M433** 关闭主路径）/ SLA 列、阶段中文名独立目录、按阶段筛选
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
