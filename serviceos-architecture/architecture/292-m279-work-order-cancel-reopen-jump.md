---
title: M279 工单取消/重开与流程人工跳转
status: Implemented
milestone: M279
lastUpdated: 2026-07-18
relatedMilestones: [M16, M19, M277, M278]
---

# M279 工单取消/重开与流程人工跳转

## 目标

交付最小可靠切片：ACTIVE/RECEIVED 工单可取消并级联关闭运行时；CANCELLED 可授权重开并重新启动根流程；ACTIVE 根流程可人工跳转到定义内单实例任务节点。

## 已实现

1. `CancelWorkOrder`：ACTIVE/RECEIVED→CANCELLED；Outbox `workorder.cancelled`；Workflow Inbox 级联取消根/子流程、节点、等待、定时器、并行汇聚、多实例与开放任务。
2. `ReopenWorkOrder`：CANCELLED→ACTIVE；`workorder.reopened`（含冻结 Bundle）；新建 ROOT 流程实例（`uq_wfl_root_work_order_open` 仅约束 ACTIVE/SUSPENDED）。
3. `WorkflowJumpService.jump`：取消当前开放运行时后激活目标任务节点（需审批引用；禁止多实例/网关目标）。
4. Flyway：workorder V106、authorization V107、workflow V108；能力 `workOrder.cancel` / `workOrder.reopen` / `workflow.jump`。
5. 事件 Schema + OpenAPI 1.0.27 时间线枚举扩展；`WorkflowCancelReopenJumpPostgresIT`。

## 明确未实现

HTTP 命令面与 Admin UI；FULFILLED→ACTIVE 纠错重开；跳转到 WAIT/TIMER/SUB_PROCESS；异常补偿编排；集合驱动多实例跳转。
