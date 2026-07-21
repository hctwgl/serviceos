---
title: M443 Admin 工单目录按创建时间筛选
version: 0.1.0
status: Implemented
milestone: M443
lastUpdated: 2026-07-21
relatedMilestones: [M435, M442]
openapiVersion: "1.0.105"
---

# M443 Admin 工单目录按创建时间筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「创建时间筛选」：目录查询支持按产品「创建时间」口径过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.105**：可选 `receivedFrom` / `receivedTo`（`format: date`） |
| Backend | Asia/Shanghai 自然日闭区间 → `received_at` 半开时间窗；写入 filterDigest；跨度上限 366 天 |
| Admin Web | 更多筛选「创建时间」RangePicker；移除「创建时间暂不可用」告警 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 产品「创建时间」= `receivedAt`，不是 `updatedAt`
- `receivedFrom` 含当日 00:00；`receivedTo` 含当日（上界为次日 00:00 排他）
- `receivedTo` 早于 `receivedFrom` 或跨度 >366 天 → 失败关闭
- 无 Flyway、无新 capability

## 4. 明确未实现

- 即将超时窗口、超过 100 的精确全量 COUNT
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
