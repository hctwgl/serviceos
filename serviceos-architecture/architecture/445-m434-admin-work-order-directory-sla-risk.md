---
title: M434 Admin 工单目录 SLA 风险旁载
version: 0.1.0
status: Implemented
milestone: M434
lastUpdated: 2026-07-21
relatedMilestones: [M234, M432, M433]
openapiVersion: "1.0.96"
---

# M434 Admin 工单目录 SLA 风险旁载

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「SLA」列：页级 `slaRiskSummaries` 旁载开放风险计数。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.96**：`WorkOrderPage.slaRiskSummaries` + `WorkOrderDirectorySlaRiskSummary` |
| Backend | PROJECT `sla.read` soft-gate（authorize）；`WorkOrderDirectorySlaRiskQuery` SPI（sla JDBC 聚合） |
| Admin Web | 「SLA」列：`开放 n / 超时 m`；缺旁载「未提供」；无风险「暂无」 |
| 证据 | PostgresIT + Playwright |

## 3. 边界

- 语义同 Network M234：open=RUNNING∪BREACHED；仅 openCount>0 入列
- 缺 `sla.read` → 省略属性（null），不伪造 `[]`/`0`
- 无 Flyway、无新 capability

## 4. 明确未实现

- 即将超时窗口、按 SLA 筛选、完整实例详情深链
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
