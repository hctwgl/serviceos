---
title: M85 工单工作区只读组合快照
status: Implemented
milestone: M85
---

# M85 工单工作区只读组合快照

## 1. 目标

接受 API-06 §2 查询元数据与 §5 `GET /work-orders/{id}/workspace` 窄切片，在 `readmodel`
中组合已实现的公开查询端口，提供不含客户 PII 的工单工作区顶层快照。

## 2. 接受范围

- API-06 §2 `meta`（asOf / freshnessStatus / queryId；projectionCheckpoint 取时间线投影
  generation 标识）；
- API-06 §5 仅顶层 workspace；不实现 `workspace/sections/{section}`、activity-summary、
  队列、SavedView、搜索或 Portal。

## 3. 组合事实

| 区块 | 来源 | 授权 |
|---|---|---|
| header | `workorder::api` WorkOrderView（已无 PII） | `workOrder.read` |
| currentTaskSummary | `task::api` WorkOrderTaskQueryService | 复用 WorkOrder 鉴权 |
| timelineFreshness | M84 projection runtime + lastProjectedAt | 同上 |
| slaSummary | `sla::api` listForWorkOrder | 缺 `sla.read` → section UNAVAILABLE |
| exceptionSummary | `operations::api` list(workOrderId) | 缺异常读权限 → UNAVAILABLE |
| allowedActionLink | 指向当前 Task 的 allowed-actions 路径 | 仅链接，不预演动作 |

currentTask：取创建时间最早的未终态 Task（READY/PENDING/CLAIMED/RUNNING/RETRY_WAIT/
MANUAL_INTERVENTION）；若无则 null。

## 4. 契约

Core OpenAPI **0.56.0** 新增 workspace 路径与 schema。无新 Flyway（实时组合，不落工作区投影表）。

## 5. 明确未实现

sections 按需加载、customer/location/vehicle/device 字段策略区块、serviceAssignmentSummary
详情、队列/SavedView/搜索、Portal、试算/结算、DATA-06 工作区持久化投影。
