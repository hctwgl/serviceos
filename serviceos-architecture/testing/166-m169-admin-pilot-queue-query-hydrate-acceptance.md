---
title: M169 Admin 专项队列 route.query 水合验收
status: Implemented
milestone: M169
lastUpdated: 2026-07-17
---

# M169 Admin 专项队列 route.query 水合验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M169-01 | 审核队列水合 | `status+taskId` 表单一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M169-02 | 整改队列水合 | `status+taskId` 表单一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M169-03 | 入站/外发队列水合 | Accepted 筛选表单一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M169-04 | 试点验收登记 | `ADMIN-PILOT-08QH` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、多 status OR、ServiceNetwork。
