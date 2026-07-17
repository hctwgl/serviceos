---
title: M171 Admin 外发关联资源与回执入站交叉深链验收
status: Implemented
milestone: M171
lastUpdated: 2026-07-17
---

# M171 Admin 外发关联资源与回执入站交叉深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M171-01 | 外发详情 → 源任务/源快照 | GET Task / Snapshot 200 | `admin-pilot-smoke.spec.ts` |
| M171-02 | 工作区 → 外发源任务 | `ob / 源任务 / {id}` GET 200 | `admin-pilot-smoke.spec.ts` |
| M171-03 | 回执 → 入站 Envelope | GET Envelope 200 | `admin-pilot-smoke.spec.ts` |
| M171-04 | 试点验收登记 | `ADMIN-PILOT-08OX` | `verify-admin-smoke.sh` |

## 明确不做

- ReviewRoute 详情、FieldOperation、SavedView、ServiceNetwork。
