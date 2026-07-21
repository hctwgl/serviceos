---
title: M444 Admin 工单目录精确全量 COUNT
version: 0.1.0
status: Implemented
milestone: M444
lastUpdated: 2026-07-21
relatedMilestones: [M436, M443]
openapiVersion: "1.0.106"
---

# M444 Admin 工单目录精确全量 COUNT

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「超过 100 的精确全量 COUNT」：当前筛选返回真实 `COUNT(*)`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.106**：`totalCount` 去掉 maximum 100；`totalCountTruncated` 说明恒 false |
| Backend | `countMatching` 精确 COUNT（同授权筛选、无 cursor）；不再 LIMIT 封顶 |
| Admin Web | 工具栏继续展示「共 N 条」 |
| 证据 | PostgresIT（>100 精确）+ MVC + Playwright |

## 3. 边界

- `totalCountTruncated` 字段保留兼容，目录路径恒为 false
- 不改 M409 关注项目角标封顶（仍上限 100）
- 不发明即将超时窗口

## 4. 明确未实现

- 即将超时窗口
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
