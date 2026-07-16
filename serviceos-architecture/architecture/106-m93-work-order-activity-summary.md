---
title: M93 工单最近活动摘要
status: Implemented
milestone: M93
---

# M93 工单最近活动摘要

## 1. 目标

接受 API-06 §5 `GET /work-orders/{id}/activity-summary`，为工作区概览提供最近活动。

## 2. 语义

- 摘要是既有工单时间线按 `(occurredAt DESC, timelineEntryId DESC)` 的最近 N 条；
- 服务端不猜测尚未定义的“关键事件”分类，不做 category/eventType 重要性过滤；
- 完整审计与分页继续使用 `/timeline` 或 `TIMELINE_AUDIT` section；
- 默认 5 条、最大 20 条，不提供 cursor；传 cursor 失败关闭为 VALIDATION_FAILED；
- 复用时间线 `workOrder.read` 与实时 Project Scope 鉴权、freshness 和最小化条目。

## 3. 实现

`WorkOrderActivitySummaryQueryService` 委托 `WorkOrderTimelineQueryService` 第一页，不直接读取
Repository、不创建第二份投影。响应复用 `WorkOrderTimelineItem` 与 API-06 查询元数据。

## 4. 契约

Core OpenAPI **0.63.0**。无新 Flyway，仍为 V080 / 82 migrations。

## 5. 明确未实现

关键事件 taxonomy/importance、过滤和分页、correlation 展开、试算/结算事件、
customer/location 敏感区块、队列/SavedView、Portal。
