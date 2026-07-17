---
title: M178 Admin 目录/SLA 项目关联深链验收
status: Implemented
milestone: M178
lastUpdated: 2026-07-17
---

# M178 Admin 目录/SLA 项目关联深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M178-01 | 工单目录 → 项目详情 | GET Project 200 | `admin-pilot-smoke.spec.ts` |
| M178-02 | 任务目录 → 项目详情 | GET Project 200 | `admin-pilot-smoke.spec.ts` |
| M178-03 | SLA 工作台展示项目深链 | RouterLink 指向 `ADMIN.PROJECT.DETAIL` | 页面代码审查 |
| M178-04 | 试点验收登记 | `ADMIN-PILOT-08DP` | `verify-admin-smoke.sh` |

## 明确不做

- QueueTable 行内单元格链接、SavedView、FieldOperation、ServiceNetwork。
