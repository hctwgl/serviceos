---
title: M164 Admin 审核/整改详情交叉深链验收
status: Implemented
milestone: M164
lastUpdated: 2026-07-17
---

# M164 Admin 审核/整改详情交叉深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M164-01 | 审核 → 资料快照 | 详情页链接 GET Snapshot 200 | `admin-pilot-smoke.spec.ts` |
| M164-02 | 整改 → 源资料快照 | 详情页链接 GET Snapshot 200 | `admin-pilot-smoke.spec.ts` |
| M164-03 | 后继审核 → 源审核 | reopen 后链接 GET 源 Case 200 | `admin-pilot-smoke.spec.ts` |
| M164-04 | 试点验收登记 | `ADMIN-PILOT-08RC` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、异常队列 query 水合、ServiceNetwork。
