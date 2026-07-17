---
title: M172 Admin 详情页明文 projectId 深链验收
status: Implemented
milestone: M172
lastUpdated: 2026-07-17
---

# M172 Admin 详情页明文 projectId 深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M172-01 | 审核详情 → 项目详情 | GET `/projects/{id}` 200 | `admin-pilot-smoke.spec.ts` |
| M172-02 | 异常 / Canonical / 外发详情展示 project 深链 | RouterLink 指向 `ADMIN.PROJECT.DETAIL` | 页面代码审查 |
| M172-03 | 试点验收登记 | `ADMIN-PILOT-08PJ` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、ReviewRoute 详情、SavedView、ServiceNetwork。
