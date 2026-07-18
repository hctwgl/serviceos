---
title: M280 取消时配置化补偿任务
status: Implemented
milestone: M280
lastUpdated: 2026-07-18
relatedMilestones: [M279]
---

# M280 取消时配置化补偿任务

## 目标

取消工单级联关闭运行时后，对声明了 `compensation` 的已完成任务节点创建补偿 SERVICE_TASK，并留下可审计的补偿请求记录。

## 已实现

1. Workflow schema：任务节点可选 `compensation.taskType` / `stageCode`；发布期禁止非任务节点声明补偿。
2. `workorder.cancelled` 消费：先收集 COMPLETED 候选 → 取消开放运行时 → 按冻结定义创建 `wfl_compensation_request` + 补偿阶段/节点/任务。
3. 幂等：`(tenant_id, cancel_event_id, source_node_instance_id)`。
4. Flyway V109；`WorkflowCompensationOnCancelPostgresIT`。

## 明确未实现

补偿完成回写、失败重试策略、边界事件/错误边界、补偿编排图、HTTP 查询面、并行补偿依赖排序。
