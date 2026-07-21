---
title: M435 Admin 工单目录独立 updatedAt
version: 0.1.0
status: Implemented
milestone: M435
lastUpdated: 2026-07-21
relatedMilestones: [M373, M434]
openapiVersion: "1.0.97"
flywayVersion: "146"
---

# M435 Admin 工单目录独立 updatedAt

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「更新时间」列对 `receivedAt` 的 MVP 映射：目录/详情基座返回独立 `updatedAt`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V146**：`wo_work_order.updated_at`；历史行按 lifecycle 时间 GREATEST 回填 |
| OpenAPI | **1.0.97**：`WorkOrder.updatedAt` required |
| Backend | 接收/激活/履约/外部详情更新/取消/重开写路径同步 bump；查询 Mapper 投影 |
| Admin Web | 「更新时间」列绑定 `updatedAt`；Tooltip 标明独立于接收时间 |
| 证据 | PostgresIT（新建同源 + activate 后前进）+ MVC + Playwright |

## 3. 边界

- 语义：工单聚合写路径最近更新时间，不是任务/SLA/分配旁载时间
- 游标仍按 `received_at,id`，本切片不改排序
- Network 目录 `receivedAt` MVP 映射不在本切片（明确未实现）
- 无新 capability

## 4. 明确未实现

- 列表 total（已由 **M436** 关闭主路径）/ 筛选扩展
- Network Portal 独立 updatedAt
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
