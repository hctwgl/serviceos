---
title: M153 Admin 工作区 TIMELINE_AUDIT → 资源详情深链验收
status: Implemented
milestone: M153
lastUpdated: 2026-07-17
---

# M153 Admin 工作区 TIMELINE_AUDIT → 资源详情深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M153-01 | Workspace TIMELINE_AUDIT → Task 详情 | 按需加载后可见「打开时间线资源」；点击 Pilot Task 链接触发 `GET /api/v1/tasks/{taskId}` 200 | `admin-pilot-smoke.spec.ts` |
| M153-02 | 试点验收登记 | `ADMIN-PILOT-08TL` | `verify-admin-smoke.sh` |

## 明确不做

- Appointment / Visit / Form / Evidence 独立详情深链；
- 未知 `resourceType` 的猜测链接；
- SavedView、inbound 列表、企业 OIDC。
