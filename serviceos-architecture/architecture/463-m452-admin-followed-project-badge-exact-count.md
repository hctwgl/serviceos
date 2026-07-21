---
title: M452 Admin 关注项目角标精确 COUNT
version: 0.1.0
status: Implemented
milestone: M452
lastUpdated: 2026-07-21
relatedMilestones: [M409, M444]
openapiVersion: "1.0.113"
---

# M452 Admin 关注项目角标精确 COUNT

## 1. 目标

关闭 Admin 关注项目角标 `UI_DATA_GAP`「超过 100 条时的精确全量 COUNT」：角标不再用 limit=100 页投影猜测。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.113**：`FollowedProjectItem.*Truncated` 语义改为具备能力时恒 false |
| Backend | 工单用 `WorkOrderPage.totalCount`；审核/整改/SLA 新增同授权 `count`（精确 COUNT(*)） |
| Admin Web | 无 UI 变更；`formatFollowedBadgeCount` 不再出现 `N+`（truncatedated 恒 false） |
| 证据 | `FollowedProjectPostgresIT` + ArchitectureTest |

## 3. 边界

- soft-gate 不变：缺能力仍省略计数字段
- `*Truncated` 字段保留兼容，有能力时恒 false
- 无 Flyway、无新 capability、无 HTTP 新路径

## 4. 明确未实现

- 项目履约使用中工单摘要 `activeWorkOrderCount` 的精确 COUNT（仍为 M422 上限 100）
- 距离角标
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
