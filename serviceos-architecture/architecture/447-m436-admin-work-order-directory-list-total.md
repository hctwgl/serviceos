---
title: M436 Admin 工单目录列表 total
version: 0.1.0
status: Implemented
milestone: M436
lastUpdated: 2026-07-21
relatedMilestones: [M409, M435]
openapiVersion: "1.0.98"
---

# M436 Admin 工单目录列表 total

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「列表无总数」：页级返回当前筛选下的封顶总数。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.98**：`WorkOrderPage.totalCount` / `totalCountTruncated` required |
| Backend | 同筛选无 cursor 的 `countMatching`（`LIMIT 101`）；上限 100，与 M409 角标一致 |
| Admin Web | 工具栏 `共 N 条` / `共 100+ 条` |
| 证据 | PostgresIT（跨页总数 + 封顶）+ MVC + Playwright |

## 3. 边界

- 不做精确全量 `COUNT(*)`；超过 100 只声明 truncated
- 计数不含 cursor；与本页 `items.length` 无关
- 客户端关键词筛选不改变服务端 total
- 无 Flyway、无新 capability

## 4. 明确未实现

- 筛选扩展（网点/师傅/阶段/SLA；区域已由 **M437** 关闭主路径）
- 超过 100 的精确全量 COUNT
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
