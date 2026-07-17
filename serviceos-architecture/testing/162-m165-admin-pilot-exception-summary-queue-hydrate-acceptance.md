---
title: M165 Admin 工作区异常摘要 → 异常队列 query 水合验收
status: Implemented
milestone: M165
lastUpdated: 2026-07-17
---

# M165 Admin 工作区异常摘要 → 异常队列 query 水合验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M165-01 | 工作区 → 异常队列深链 | 链接携带 `workOrderId` 与 `status=OPEN` | `admin-pilot-smoke.spec.ts` |
| M165-02 | 队列页 query 水合 | 表单 workOrderId/status 与 URL 一致；GET 200 | `admin-pilot-smoke.spec.ts` |
| M165-03 | 试点验收登记 | `ADMIN-PILOT-08EH` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、多 status OR、ServiceNetwork。
