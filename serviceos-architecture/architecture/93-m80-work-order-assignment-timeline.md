---
title: M80 工单服务分配生命周期时间线事件合并
status: Implemented
milestone: M80
---

# M80 工单服务分配生命周期时间线事件合并

## 1. 目标

在既有工单时间线投影上合并已发布的 ServiceAssignment 激活生命周期事件，使派单握手与超时在同一
`workOrder.read` 授权时间线中可见。

不发明 ServiceNetwork 目录/准入规则，不实现试算/结算、checkpoint/重建（DATA-06 仍为 Proposed）或 Portal。

## 2. 模块边界

- 事件载荷已含权威 `workOrderId` / `taskId`，不新增跨模块查询端口；
- 握手四类事件仅接受 `@v2`；`activation-completed` / `activation-abort-completed` /
  `activation-timed-out` 接受 `@v1`；
- `activation-timed-out` 信封模块为 `dispatch`、聚合类型为 `ServiceAssignmentActivationSaga`，
  投影 `resourceType` 仍为 `ServiceAssignment`；
- 不投影 `assigneeId`、`capacityReservationId`、`guardId`、`preparedTaskAssignmentId` 或自由文本。

## 3. 映射

| 事件 | schema | outcome | actor |
|---|---|---|---|
| pending-activation | v2 | `reasonCode` 或 `PENDING_ACTIVATION` | `initiatedBy` |
| task-prepared | v2 | `reasonCode` 或 `TASK_PREPARED` | `initiatedBy` |
| activated | v2 | `reasonCode` 或 `ACTIVATED` | `initiatedBy` |
| activation-aborted | v2 | `reasonCode` 或 `ABORTED` | `initiatedBy` |
| activation-completed | v1 | `reasonCode` 或 `ACTIVATION_COMPLETED` | null |
| activation-abort-completed | v1 | `reasonCode` 或 `ABORT_COMPLETED` | null |
| activation-timed-out | v1 | `errorCode` | null |

## 4. 契约

- Flyway V076 expand `ASSIGNMENT` category；
- Core OpenAPI 0.51.0 扩展 timeline `x-extensible-enum`（category / eventType / resourceType）。

## 5. 明确未实现

ServiceNetwork 生命周期、试算/结算、checkpoint/重建、Portal、评分策略与完整派单 UI。
