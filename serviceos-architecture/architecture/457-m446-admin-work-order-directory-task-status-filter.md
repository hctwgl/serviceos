---
title: M446 Admin 工单目录按任务状态筛选
version: 0.1.0
status: Implemented
milestone: M446
lastUpdated: 2026-07-21
relatedMilestones: [M432, M438, M445]
openapiVersion: "1.0.108"
---

# M446 Admin 工单目录按任务状态筛选

## 1. 目标

关闭 Admin 工单目录母版「任务状态」筛选缺口：与最早 ACTIVE 任务同口径过滤，并旁载列。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.108**：`currentTaskStatus` query + `WorkOrder.currentTaskStatus` |
| Backend | task SPI `findCurrentTaskStatuses` / `findWorkOrderIdsByCurrentTaskStatus` → SQL IN；filterDigest |
| Admin Web | 更多筛选「任务状态」+ 表格列 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- ACTIVE 集：READY/PENDING/CLAIMED/RUNNING/RETRY_WAIT/MANUAL_INTERVENTION
- 与阶段旁载同一 DISTINCT ON 任务
- 无 Flyway、无新 capability

## 4. 明确未实现

- 审核/整改状态筛选、服务端关键词检索
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
