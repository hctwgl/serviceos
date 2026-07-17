---
title: M155 Admin 预约/表单提交详情页验收
status: Implemented
milestone: M155
lastUpdated: 2026-07-17
---

# M155 Admin 预约/表单提交详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M155-01 | Workspace → 预约详情 | 预约 CONFIRMED 后可见「打开预约详情」；点击触发 `GET /api/v1/appointments/{id}` 200 | `admin-pilot-smoke.spec.ts`（现场履约） |
| M155-02 | Workspace → 表单提交详情 | Task 完结后可见「打开表单提交详情」；点击触发 `GET /api/v1/form-submissions/{id}` 200 | `admin-pilot-smoke.spec.ts`（完结） |
| M155-03 | 试点验收登记 | `ADMIN-PILOT-08AD` | `verify-admin-smoke.sh` |

## 明确不做

- Visit / EvidenceItem 独立详情页；
- 详情页写命令；
- SavedView、inbound 列表、企业 OIDC。
