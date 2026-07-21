---
title: M433 Admin 工单目录当前责任人列验收矩阵
version: 0.1.0
status: Implemented
milestone: M433
lastUpdated: 2026-07-21
---

# M433 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 已认领 + Persona | `currentClaimedBy` + `currentAssigneeDisplayName` | WorkOrderQueryPostgresIT |
| A2 | 无认领 | 两字段 null；UI「未提供」 | PostgresIT + 前端回退 |
| A3 | 已认领无档案 | claimedBy 有值、displayName null；UI「未提供」+ Tooltip | 实现语义 |
| A4 | Admin 展示 | 责任人列显示 Persona 名 | Playwright |
| A5 | 契约 | OpenAPI 1.0.95；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
