---
title: M162 Admin 最近活动资源详情深链验收
status: Implemented
milestone: M162
lastUpdated: 2026-07-17
---

# M162 Admin 最近活动资源详情深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M162-01 | 最近活动白名单旁链 | 与核心时间线同构 allow-list | `WorkOrderWorkspacePage.vue` |
| M162-02 | Admin 固定 Pilot 深链 | 最近活动 → Task GET 200 | `admin-pilot-smoke.spec.ts` |
| M162-03 | 试点验收登记 | `ADMIN-PILOT-08AS` | `verify-admin-smoke.sh` |

## 明确不做

- 关键事件 taxonomy、FieldOperation 详情、SavedView、ServiceNetwork。
