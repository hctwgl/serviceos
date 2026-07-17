---
title: M173 Admin 详情页源资源明文字段深链验收
status: Implemented
milestone: M173
lastUpdated: 2026-07-17
---

# M173 Admin 详情页源资源明文字段深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M173-01 | 外发事实格 sourceTaskId → 任务详情 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M173-02 | 回执 / 异常 / 审核 / 整改事实格源 UUID 可点 | RouterLink 指向已有详情路由 | 页面代码审查 |
| M173-03 | 试点验收登记 | `ADMIN-PILOT-08OI` | `verify-admin-smoke.sh` |

## 明确不做

- ReviewRoute 详情、FieldOperation、SavedView、ServiceNetwork。
