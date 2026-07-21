---
title: M439 Admin 工单目录网点/师傅列
version: 0.1.0
status: Implemented
milestone: M439
lastUpdated: 2026-07-21
relatedMilestones: [M433, M438]
openapiVersion: "1.0.101"
---

# M439 Admin 工单目录网点/师傅列

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「网点/师傅」列：目录投影返回 ACTIVE 服务责任网点与师傅及显示名。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.101**：`currentNetworkId` / `currentNetworkDisplayName` / `currentTechnicianId` / `currentTechnicianDisplayName` required nullable |
| Backend | `WorkOrderDirectoryServiceResponsibilityQuery`（dispatch）+ `NetworkDirectoryLabelQuery`（network）；工单级 ACTIVE NETWORK/TECHNICIAN |
| Admin Web | 「网点/师傅」列：显示名优先；缺档「未提供」；Tooltip 保留 ID |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径：`dsp_service_assignment` 中 status=ACTIVE 的 NETWORK/TECHNICIAN；同级多条取 `effective_from` 最新
- 无 ACTIVE 责任 → 字段 null；显示名缺档 → null，不发明名称
- 本列不是「当前责任人」（claimed_by，见 M433）
- 无 Flyway、无新 capability；不 soft-gate（随 workOrder.read 基座返回）

## 4. 明确未实现

- 按师傅筛选已由 M441 关闭；SLA/创建时间筛选、即将超时窗口、超过 100 精确 COUNT 仍开放
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
