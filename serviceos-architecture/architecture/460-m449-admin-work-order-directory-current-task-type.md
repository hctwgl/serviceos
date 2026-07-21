---
title: M449 Admin 工单目录当前任务列
version: 0.1.0
status: Implemented
milestone: M449
lastUpdated: 2026-07-21
relatedMilestones: [M432, M446, M448]
openapiVersion: "1.0.111"
---

# M449 Admin 工单目录当前任务列

## 1. 目标

关闭 Admin 工单目录母版「当前任务」列缺口：旁载最早 ACTIVE 任务的 `task_type`（与工作区 currentTaskSummary 同口径）。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.111**：`WorkOrder.currentTaskType` |
| Backend | task SPI sidecar 增加 `task_type` → `findCurrentTaskTypes` |
| Admin Web | 列「当前任务」→ `statusLabel(currentTaskType)` |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 与阶段/任务状态同一 DISTINCT ON ACTIVE 任务
- 无筛选参数、无 Flyway、无新 capability

## 4. 明确未实现

- 异常摘要列（已由 **M450** 关闭）
- 按 taskType 筛选、任务深链
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
