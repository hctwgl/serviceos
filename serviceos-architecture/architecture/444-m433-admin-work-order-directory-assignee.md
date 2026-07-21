---
title: M433 Admin 工单目录当前责任人列
version: 0.1.0
status: Implemented
milestone: M433
lastUpdated: 2026-07-21
relatedMilestones: [M432, M373]
openapiVersion: "1.0.95"
---

# M433 Admin 工单目录当前责任人列

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「当前责任人」列：目录投影返回当前 ACTIVE 任务认领主体及 Persona 显示名。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.95**：`WorkOrder.currentClaimedBy` / `currentAssigneeDisplayName` required nullable |
| Backend | `WorkOrderDirectoryAssigneeQuery` SPI（与阶段共用 task JDBC DISTINCT ON）；Persona `displayNames` 批量解析 |
| Admin Web | 「当前责任人」列优先显示名；无显示名「未提供」；Tooltip 保留主体 ID |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径：首个 ACTIVE 任务的 `claimed_by`（非网点/师傅 ServiceAssignment）
- 无认领 / 无 PersonProfile → 对应字段 null；不发明名称、不伪造 0
- 本列不是「网点/师傅」列
- 无 Flyway、无新 capability

## 4. 明确未实现

- SLA 列 / sla.read soft-gate
- 网点/师傅列、按责任人筛选
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
