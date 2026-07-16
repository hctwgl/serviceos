---
title: M75 工单 SLA 时间线事件合并
status: Implemented
milestone: M75
---

# M75 工单 SLA 时间线事件合并

## 1. 目标

在 M73/M74 已建立的工单时间线投影上，合并已发布的 Task ELAPSED SLA 公开事件
`sla.started` / `sla.breached` / `sla.met`，使授权时间线可见 SLA 开始、违约与达成事实。
不改变查询授权、分页与 `freshnessStatus=UNKNOWN`，也不回写 SLA 领域聚合。

## 2. 模块与事实边界

- 继续由同一 Inbox 消费者 `readmodel.work-order-core-timeline.v1` 写入
  `rdm_work_order_timeline_entry`；
- `sla.started` 载荷已含 `workOrderId` / `projectId`；
- `sla.breached` / `sla.met` 仅含 `taskId`，通过既有 `task::api` `TaskTimelineContextQuery`
  解析工单范围，不跨模块读取 `sla_*` 或 `tsk_*`；
- 非工单 Task 的 SLA 事件 Inbox 完成但不投影；
- 不保存 policy digest、elapsedSeconds、deadline 明细正文或自由文本；只保留稳定编码与双时间。

## 3. 支持的 SLA 事件

| 事件 | category | resourceType | resourceId | resourceCode | outcomeCode |
|---|---|---|---|---|---|
| `sla.started@v1` | `SLA` | `SlaInstance` | `slaInstanceId` | `slaRef` | `STARTED` |
| `sla.breached@v1` | `SLA` | `SlaInstance` | `slaInstanceId` | null | `BREACHED` |
| `sla.met@v1` | `SLA` | `SlaInstance` | `slaInstanceId` | null | payload `status`（`MET`/`MET_LATE`） |

## 4. 查询与契约

- 查询/授权/分页与 M73/M74 一致；
- V073 expand category 允许 `SLA`；
- Core OpenAPI 0.46.0 将 `SLA` / `SlaInstance` / 三个 `sla.*` 事件加入
  `x-extensible-enum`。

## 5. 明确未实现

Evidence/Review、Delivery、OperationalException、试算/结算合并；BUSINESS 日历、暂停/恢复、
预警/升级/通知事件；Broker checkpoint、投影重建作业、搜索、导出和 Portal。
