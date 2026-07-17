---
title: M161 Admin 核心时间线资源详情深链验收
status: Implemented
milestone: M161
lastUpdated: 2026-07-17
---

# M161 Admin 核心时间线资源详情深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M161-01 | 核心时间线白名单旁链 | FormSubmission / EvidenceSetSnapshot 等已有详情页可点 | `WorkOrderWorkspacePage.vue` |
| M161-02 | TIMELINE_AUDIT 白名单扩展 | FormSubmission / EvidenceItem / EvidenceSetSnapshot 可链 | 同构 `TIMELINE_RESOURCE_ROUTES` |
| M161-03 | Admin 完结后深链 | 核心时间线 → FormSubmission / Snapshot GET 200 | `admin-pilot-smoke.spec.ts` |
| M161-04 | 试点验收登记 | `ADMIN-PILOT-08CT` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork、企业 OIDC/BFF。
