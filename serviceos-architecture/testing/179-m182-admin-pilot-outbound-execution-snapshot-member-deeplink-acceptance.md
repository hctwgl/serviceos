---
title: M182 Admin 外发执行任务与快照成员深链验收
status: Implemented
milestone: M182
lastUpdated: 2026-07-17
---

# M182 Admin 外发执行任务与快照成员深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M182-01 | 外发详情 → 执行任务 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M182-02 | 资料快照成员 → 资料项 | GET EvidenceItem 200 | `admin-pilot-smoke.spec.ts` |
| M182-03 | 试点验收登记 | `ADMIN-PILOT-08EM` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation、ReviewRoute、SavedView、ServiceNetwork。
