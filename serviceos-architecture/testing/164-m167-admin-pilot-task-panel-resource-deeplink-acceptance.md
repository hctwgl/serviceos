---
title: M167 Admin Task 面板资源详情深链验收
status: Implemented
milestone: M167
lastUpdated: 2026-07-17
---

# M167 Admin Task 面板资源详情深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M167-01 | Task 面板 → Contact/Appointment/Visit | GET 详情 200 | `admin-pilot-smoke.spec.ts` |
| M167-02 | Task 面板 → FormSubmission/EvidenceItem | GET 详情 200（新页签） | `admin-pilot-smoke.spec.ts` |
| M167-03 | 试点验收登记 | `ADMIN-PILOT-08TP` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork。
