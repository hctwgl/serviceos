---
title: M87 工单工作区按需区块加载
status: Implemented
milestone: M87
---

# M87 工单工作区按需区块加载

## 1. 目标

接受 API-06 §5 `GET /work-orders/{id}/workspace/sections/{section}` 窄切片，仅为
`TASKS` 与 `TIMELINE_AUDIT` 提供实时组合按需加载，延续 M85 顶层工作区模式。

## 2. 接受范围

- API-06 §5 仅 `TASKS`、`TIMELINE_AUDIT`；
- 复用 `workOrder.read` 与既有 `task::api` / 时间线查询端口；
- 查询元数据沿用 §2（asOf / freshnessStatus / queryId / projectionCheckpoint）。

## 3. 行为

| section | 载荷 | 说明 |
|---|---|---|
| TASKS | 工单内 Task 摘要分页 | 映射为 workspace TaskSummary；支持 cursor/limit |
| TIMELINE_AUDIT | 时间线条目分页 | 复用时间线 item + freshness；支持 cursor/limit |
| 其他枚举值 | 400 VALIDATION_FAILED | 本切片未接受，失败关闭 |

不含客户 PII；不落工作区/区块投影表。

## 4. 契约

Core OpenAPI **0.57.0**。无新 Flyway。

## 5. 明确未实现

其余 section、`activity-summary`、队列/SavedView、Portal、区块持久化投影。
