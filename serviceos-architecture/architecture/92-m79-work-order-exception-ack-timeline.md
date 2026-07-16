---
title: M79 工单运营异常确认时间线事件合并
status: Implemented
milestone: M79
---

# M79 工单运营异常确认时间线事件合并

## 1. 目标

为 OperationalException 增加公开最小查询端口 `ExceptionTimelineContextQuery`，并在既有工单时间线投影上
合并 `operational.exception.acknowledged@v1`。

不实现试算/结算、checkpoint/重建（DATA-06 仍为 Proposed）或 Portal。

## 2. 模块边界

- `operations::api` 暴露 `ExceptionTimelineContext(exceptionId, projectId, workOrderId)`；
- 查询通过工作台权威行解析 Task（`task_id` 或 `source_type=TASK` 的 `source_id`），再经
  `TaskTimelineContextQuery` 得到工单范围；
- 无 Task 链接的异常不猜测工单归属，记 Inbox 已消费但不投影；
- `readmodel` 通过 `allowedDependencies` 依赖 `operations::api`；
- 不投影错误码、备注、处理 Task 或审计字段。

## 3. 映射

| 事件 | outcome | actor |
|---|---|---|
| acknowledged | `ACKNOWLEDGED` | `acknowledgedBy` |

## 4. 契约

Core OpenAPI 0.50.0 扩展 timeline `x-extensible-enum`。无新 Flyway（V075 已含 `EXCEPTION`）。

## 5. 明确未实现

试算/结算、checkpoint/重建、Portal、通知中心。
