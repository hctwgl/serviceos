---
title: M422 Admin 履约配置中心使用中工单摘要
version: 0.1.0
status: Implemented
milestone: M422
lastUpdated: 2026-07-21
relatedMilestones: [M385, M388, M409]
openapiVersion: "1.0.88"
---

# M422 Admin 履约配置中心使用中工单摘要

## 1. 目标

关闭 M385 `ADMIN.PROJECT.FULFILLMENT.LIST` UI_DATA_GAP「使用中工单数」：以项目 ACTIVE 工单计数摘要驱动 SummaryStrip，口径对齐 M409 关注项目角标。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.88** `GET /projects/{projectId}/fulfillment-usage-summary` |
| Configuration | `usageSummary`：硬门禁 `project.fulfillment.read`；`workOrder.read` soft-gate（缺能力 count=null） |
| 计数 | `WorkOrderQuery(status=ACTIVE, limit=100)`；`truncated` 表示超出上限 |
| Admin Web | 履约配置中心 SummaryStrip 消费真实计数；移除 UI_DATA_GAP 文案 |
| 证据 | `ProjectFulfillmentProfilePostgresIT` + ArchitectureTest + Playwright |

## 3. 明确未实现

- 超过 100 的精确 `COUNT(*)`
- 按 `serviceProductCode` 分拆使用中工单
- 失败登录 / 设备指纹
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
