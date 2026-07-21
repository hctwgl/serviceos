---
title: M441 Admin 工单目录按师傅筛选
version: 0.1.0
status: Implemented
milestone: M441
lastUpdated: 2026-07-21
relatedMilestones: [M439, M440]
openapiVersion: "1.0.103"
---

# M441 Admin 工单目录按师傅筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「按师傅筛选」：目录查询支持与 `currentTechnicianId` 列同口径的精确师傅过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.103**：可选 `currentTechnicianId` query（uuid） |
| Backend | dispatch SPI `findWorkOrderIdsByActiveTechnicianId` → 授权 SQL `id IN (...)`；写入 cursor filterDigest |
| Admin Web | 更多筛选「服务师傅」Select（`/technician-profiles` ACTIVE）；路由/SavedView 水合 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径与 M439 列一致：ACTIVE TECHNICIAN `assignee_id` = 师傅档案 UUID 字符串
- 无 ACTIVE TECHNICIAN 的工单不会命中任何师傅筛选
- 项目范围由授权 SQL 收敛；无 Flyway、无新 capability

## 4. 明确未实现

- SLA/创建时间筛选、即将超时窗口、超过 100 的精确全量 COUNT
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
