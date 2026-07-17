---
title: M175 Admin 运营异常 handlingTaskId 深链验收
status: Implemented
milestone: M175
lastUpdated: 2026-07-17
---

# M175 Admin 运营异常 handlingTaskId 深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M175-01 | 异常队列 → 异常详情 | GET Exception 200 | `admin-pilot-smoke.spec.ts` |
| M175-02 | 异常详情 → 人工接管任务 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M175-03 | 试点验收登记 | `ADMIN-PILOT-08HT` | `verify-admin-smoke.sh` |

## 明确不做

- 多态 sourceId 深链、FieldOperation、SavedView、ServiceNetwork。
