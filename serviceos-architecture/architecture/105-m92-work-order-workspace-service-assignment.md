---
title: M92 工单工作区服务责任摘要
status: Implemented
milestone: M92
---

# M92 工单工作区服务责任摘要

## 1. 目标

补齐 API-06 §5 已接受顶层工单工作区的 `serviceAssignmentSummary`，实时展示当前 Task
已激活的网点与师傅责任。

## 2. 接受范围

- 仅查询工作区 `currentTaskSummary` 对应 Task 的 ACTIVE ServiceAssignment；
- 入口继续要求 `workOrder.read`；摘要单独要求 `dispatch.read` 与实时 Project Scope；
- 缺权时摘要为 null、`SERVICE_ASSIGNMENT=UNAVAILABLE`，不把整个工作区打成 403；
- 有权但无当前 Task 或无 ACTIVE 责任时为 EMPTY；
- 网点与师傅分别保留 `effectiveFrom` 和稳定 `reassignmentReasonCode`，不合并为歧义时间；
- 不返回 assignment/saga/guard/decision/authority 内部 ID、容量、评分或操作者。

## 3. 权威来源

`readmodel` 经 `dispatch::api` `ServiceAssignmentQueryService.findActiveForTask` 实时组合；
Dispatch 使用 Task 权威事实确定 Project Scope，并通过既有 ACTIVE 唯一索引查询。

## 4. 契约与数据库

- Core OpenAPI **0.62.0**；
- Flyway **V080** 新增 NORMAL 风险 `dispatch.read` capability；无业务表结构变化。

## 5. 明确未实现

`FACTS_CALCULATIONS`、历史责任、PENDING_ACTIVATION/saga/容量详情、ServiceNetwork 名称目录、
activity-summary、队列/SavedView、Portal、工作区持久化投影。
