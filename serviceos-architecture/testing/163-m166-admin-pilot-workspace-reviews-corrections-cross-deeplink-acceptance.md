---
title: M166 Admin 工作区审核/整改关联资源深链验收
status: Implemented
milestone: M166
lastUpdated: 2026-07-17
---

# M166 Admin 工作区审核/整改关联资源深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M166-01 | 区块 → 审核资料快照 | `rc / Snapshot / {id}` GET Snapshot 200 | `admin-pilot-smoke.spec.ts` |
| M166-02 | 区块 → 后继源审核 | `rc / 源审核 / {id}` GET Review 200 | `admin-pilot-smoke.spec.ts` |
| M166-03 | 试点验收登记 | `ADMIN-PILOT-08RW` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork、猜测 Correction 源快照字段。
