---
title: M170 Admin 目录页 route.query 水合验收
status: Implemented
milestone: M170
lastUpdated: 2026-07-17
---

# M170 Admin 目录页 route.query 水合验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M170-01 | 工单/任务目录水合 | projectId+status 表单一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M170-02 | SLA/项目目录水合 | Accepted 筛选表单一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M170-03 | 试点验收登记 | `ADMIN-PILOT-08DH` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork。
