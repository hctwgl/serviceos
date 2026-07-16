---
title: M81 工单 Task 指派与执行保护时间线事件合并
status: Implemented
milestone: M81
---

# M81 工单 Task 指派与执行保护时间线事件合并

## 1. 目标

在既有工单时间线投影上合并已发布的 Task 侧指派、改派握手、执行保护与人工接管事件，补齐
M80 ServiceAssignment 生命周期后的 Task 侧可见性。

不实现 DATA-06 checkpoint/重建、ServiceNetwork 生命周期、试算/结算或 Portal。

## 2. 模块边界

- 经既有 `TaskTimelineContextQuery` 解析工单范围；无工单链接的运营 Task 记 Inbox 已消费但不投影；
- 聚合信封为 `task` / `Task`；category 沿用 `TASK`，无需新 Flyway；
- 不投影 `candidatePrincipalIds`、`sourceId`、`preparationKey`、`serviceAssignmentId`、
  `guardId`/`guardKey`、`businessKey`、`resultRef` 或 Attempt 正文。

## 3. 映射

| 事件 | outcome | actor |
|---|---|---|
| `task.assigned` | `ASSIGNED` | null |
| `task.assignment-prepared` | `reasonCode` 或 `PREPARED` | `principalId` |
| `task.assignment-activated` | `reasonCode` 或 `ACTIVATED` | `principalId` |
| `task.assignment-aborted` | `reasonCode` 或 `ABORTED` | `principalId` |
| `task.execution-guard.activated` | `reasonCode` 或 `GUARD_ACTIVATED` | null |
| `task.execution-guard.released` | `reasonCode` 或 `GUARD_RELEASED` | null |
| `task.execution.manual-intervention-required` | `errorCode` | null（时间为信封 `occurredAt`） |

## 4. 契约

Core OpenAPI 0.52.0 扩展 timeline `eventType` `x-extensible-enum`。无新 Flyway（沿用 V076 / TASK）。

## 5. 明确未实现

evidence revision/slots 技术噪声、checkpoint/重建、ServiceNetwork、试算/结算、Portal。
