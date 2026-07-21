---
title: M442 Admin 工单目录按 SLA 风险筛选
version: 0.1.0
status: Implemented
milestone: M442
lastUpdated: 2026-07-21
relatedMilestones: [M434, M441]
openapiVersion: "1.0.104"
---

# M442 Admin 工单目录按 SLA 风险筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「按 SLA 筛选」：目录查询支持与 SLA 列同口径的 OPEN/BREACHED 过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.104**：可选 `slaRisk` enum `[OPEN, BREACHED]` |
| Backend | sla SPI `findWorkOrderIdsBySlaRisk`；仅在具备 `sla.read` 范围内解析 → 授权 SQL IN；写入 filterDigest |
| Admin Web | 更多筛选「SLA 风险」Select（有开放风险 / 已超时） |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- OPEN = RUNNING∪BREACHED；BREACHED = 仅 BREACHED（同 M434 列）
- 无 `sla.read` → 筛选结果为空集，不泄露 SLA 事实
- 不交付「即将超时」时间窗（仍为独立 UI_DATA_GAP）
- 无 Flyway、无新 capability

## 4. 明确未实现

- 创建时间筛选、即将超时窗口、超过 100 的精确全量 COUNT
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
