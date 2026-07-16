---
title: M83 工单条件处置时间线事件合并
status: Implemented
milestone: M83
---

# M83 工单条件处置时间线事件合并

## 1. 目标

在既有工单时间线投影上合并已发布的 `evidence.condition-disposition-recorded@v1`，使 M53
KEEP/INVALIDATE 条件处置在 `workOrder.read` 时间线中可见。

补齐 M76 延后的条件处置可见性；不合并 revision/slots 技术噪声，不实现 DATA-06 checkpoint/重建。

## 2. 模块边界

- 经既有 `TaskTimelineContextQuery` 解析工单；无工单链接的运营 Task 记 Inbox 已消费但不投影；
- 聚合信封为 `evidence` / `EvidenceConditionDisposition`；
- category 沿用 `EVIDENCE`，无需新 Flyway；
- 不投影 `reviewRef`、`resolutionId`、`slotId`、`reasonCode`、`affectedRevisionCount`。

## 3. 映射

| 事件 | outcome | actor |
|---|---|---|
| `evidence.condition-disposition-recorded` | `decision`（KEEP / INVALIDATE） | null |

## 4. 契约

Core OpenAPI 0.54.0 扩展 timeline `eventType` / `resourceType` `x-extensible-enum`。无新 Flyway。

## 5. 明确未实现

revision/slots 技术噪声、checkpoint/重建、试算/结算、Portal。
