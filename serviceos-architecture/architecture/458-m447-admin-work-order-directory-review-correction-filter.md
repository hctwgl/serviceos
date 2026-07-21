---
title: M447 Admin 工单目录审核/整改状态筛选
version: 0.1.0
status: Implemented
milestone: M447
lastUpdated: 2026-07-21
relatedMilestones: [M442, M445, M446]
openapiVersion: "1.0.109"
---

# M447 Admin 工单目录审核/整改状态筛选

## 1. 目标

关闭 Admin 工单目录母版「审核/整改状态」更多筛选缺口：按运营桶过滤待审核与整改中工单。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.109**：`reviewCorrectionStatus` ∈ `REVIEW_OPEN` / `CORRECTION_ACTIVE` |
| Backend | evidence SPI → `task_id`→`work_order_id`；`evidence.read` soft-gate；SQL IN + filterDigest |
| Admin Web | 更多筛选「审核/整改状态」（待审核 / 整改中） |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- `REVIEW_OPEN`：EXISTS OPEN ReviewCase
- `CORRECTION_ACTIVE`：EXISTS OPEN|IN_PROGRESS|RESUBMITTED CorrectionCase
- 无旁载列、无 Flyway、无新 capability

## 4. 明确未实现

- 服务端关键词检索
- 目录审核/整改旁载列或页级摘要
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
