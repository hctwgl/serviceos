---
title: M423 Admin 工单工作区脱敏客户联系摘要
version: 0.1.0
status: Implemented
milestone: M423
lastUpdated: 2026-07-21
relatedMilestones: [M85, M351, M389]
openapiVersion: "1.0.89"
---

# M423 Admin 工单工作区脱敏客户联系摘要

## 1. 目标

关闭 M389 `ADMIN.WORKORDER.WORKSPACE` UI_DATA_GAP「客户/地址/联系方式正式脱敏读模型仍缺」：将既有 `WorkOrderQueryService.getMaskedContact`（M351）并入 Admin 工单工作区顶层快照。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.89** `WorkOrderWorkspace` 增加 `maskedCustomerName` / `maskedCustomerPhone` / `maskedServiceAddress` |
| ReadModel | `DefaultWorkOrderWorkspaceQueryService` 复用脱敏端口；不返回原文 |
| Admin Web | 工作区摘要区 `SensitiveText` 展示脱敏字段 |
| 证据 | `WorkOrderWorkspacePostgresIT` + SecurityTest + Playwright |

## 3. 权限

- 复用 `workOrder.read`（与工作区顶层及终审脱敏一致）
- 无新增 capability；无 Flyway

## 4. 明确未实现

- Network 工作区脱敏客户联系（需网点范围授权路径）
- 表单资料缩略图与完整审核记录产品化
- 工单目录列表客户字段
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
