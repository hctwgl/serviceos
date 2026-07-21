---
title: M440 Admin 工单目录按网点筛选
version: 0.1.0
status: Implemented
milestone: M440
lastUpdated: 2026-07-21
relatedMilestones: [M439, M438]
openapiVersion: "1.0.102"
---

# M440 Admin 工单目录按网点筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「按网点筛选」：目录查询支持与 `currentNetworkId` 列同口径的精确网点过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.102**：可选 `currentNetworkId` query（uuid） |
| Backend | dispatch SPI `findWorkOrderIdsByActiveNetworkId`（DISTINCT ON 最新 ACTIVE NETWORK）→ 授权 SQL `id IN (...)`；写入 cursor filterDigest |
| Admin Web | 更多筛选「服务网点」Select（`/service-networks` ACTIVE 选项）；路由/SavedView 水合 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径与 M439 列一致：ACTIVE NETWORK `assignee_id` = 网点 UUID 字符串
- 无 ACTIVE NETWORK 的工单不会命中任何网点筛选
- workorder 不直接读 `dsp_service_assignment`；经 SPI + Spring 装配；项目范围仍由授权 SQL 收敛
- 无 Flyway、无新 capability

## 4. 明确未实现

- 师傅/SLA/创建时间筛选
- 即将超时窗口、超过 100 的精确全量 COUNT
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
