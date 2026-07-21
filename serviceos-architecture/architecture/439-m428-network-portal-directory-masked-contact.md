---
title: M428 Network Portal 目录页脱敏客户联系
version: 0.1.0
status: Implemented
milestone: M428
lastUpdated: 2026-07-21
relatedMilestones: [M236, M391, M424]
openapiVersion: "1.0.92"
---

# M428 Network Portal 目录页脱敏客户联系

## 1. 目标

关闭 M424/ADR-074 残留「工单/任务目录列表客户字段」：在 Network Portal 工单与任务目录 items 上投影服务端脱敏客户联系。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.92** `NetworkPortalWorkOrderItem` / `NetworkPortalTaskItem` 增加 required nullable `maskedCustomerName` / `maskedCustomerPhone` / `maskedServiceAddress` |
| workorder | 复用 `getMaskedContactForNetwork`；原文不离开模块 |
| ReadModel | `listWorkOrders` / `listTasks` 在 ACTIVE 责任聚合后装配脱敏字段（不 soft-omit） |
| Network Web | 工单/任务目录「客户 / 联系电话 / 服务地址」列 |
| 证据 | `NetworkPortalReadPostgresIT` + Playwright |

## 3. 权限与边界

- 基座：ACTIVE NetworkMembership + NETWORK `networkTask.read` + 本网点 ACTIVE NETWORK assignment
- 脱敏端口再次强制 `networkTask.read`；目录项始终序列化三字段（缺值为 null）
- 只读事务；无 Flyway；无新 capability

## 4. 明确未实现

- Admin 工单目录客户列（已由 **M429** 交付）
- 独立 `updatedAt`、目录 reviews、notifications、Portal ACK
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
