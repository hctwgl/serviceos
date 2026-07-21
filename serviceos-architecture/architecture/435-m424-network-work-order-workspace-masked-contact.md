---
title: M424 Network 工单工作区脱敏客户联系摘要
version: 0.1.0
status: Implemented
milestone: M424
lastUpdated: 2026-07-21
relatedMilestones: [M213, M351, M391, M423]
openapiVersion: "1.0.90"
---

# M424 Network 工单工作区脱敏客户联系摘要

## 1. 目标

关闭 M391 `NETWORK.WORKORDER.WORKSPACE` UI_DATA_GAP「客户/地址脱敏读模型未就绪」：在 Network Portal 限定工单工作区顶层快照中投影服务端脱敏客户联系，且不复用 Admin `WorkOrderWorkspace`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.90** `NetworkPortalWorkOrderWorkspace` 增加 required nullable `maskedCustomerName` / `maskedCustomerPhone` / `maskedServiceAddress` |
| workorder | `WorkOrderQueryService.getMaskedContactForNetwork`：强制 NETWORK `networkTask.read`；复用脱敏算法；原文不离开模块 |
| ReadModel | `DefaultNetworkPortalQueryService#getWorkOrderWorkspace` 在 ACTIVE 责任证明后装配脱敏字段 |
| Network Web | 工作区 object-head 展示脱敏客户/手机/地址 |
| 证据 | `NetworkPortalReadPostgresIT` + SecurityTest + Playwright |

## 3. 权限与事务

- 基座：ACTIVE NetworkMembership + NETWORK `networkTask.read` + 本网点 ACTIVE NETWORK assignment
- 脱敏端口再次强制 `networkTask.read`（失败关闭）；不 soft-omit
- 只读事务；无 Flyway；无新 capability

## 4. 明确未实现

- 工单/任务目录列表客户字段（已由 **M428** 交付）
- 表单资料缩略图与完整审核记录产品化（Admin 预览/审核已由 M425～M427 交付主路径）
- Admin 工单目录客户列
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
