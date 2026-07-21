---
title: M429 Admin 工单目录脱敏客户联系
version: 0.1.0
status: Implemented
milestone: M429
lastUpdated: 2026-07-21
relatedMilestones: [M68, M351, M373, M423, M428]
openapiVersion: "1.0.93"
---

# M429 Admin 工单目录脱敏客户联系

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「客户」列：在授权工单目录/详情 `WorkOrder` 上投影服务端脱敏客户联系。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.93** `WorkOrder` 增加 required nullable `maskedCustomerName` / `maskedCustomerPhone` / `maskedServiceAddress` |
| workorder | `list` / `get` 在已授权视图上附着 `maskRawContact`；原文不离开模块 |
| Admin Web | 工单中心「客户」列展示脱敏姓名/电话/地址 |
| 证据 | `WorkOrderQueryPostgresIT` + Playwright |

## 3. 权限与边界

- 复用 `workOrder.read` 实时项目范围；无新 capability
- 脱敏字段随基座返回（不 soft-omit）；缺值为 null
- 只读事务；无 Flyway

## 4. 明确未实现

- 服务端按客户名/手机后四位/地址关键词检索
- 目录阶段/责任人/SLA/独立 updatedAt 列
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
