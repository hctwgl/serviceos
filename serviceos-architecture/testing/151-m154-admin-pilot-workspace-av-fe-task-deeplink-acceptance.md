---
title: M154 Admin 工作区预约上门/表单资料 → Task 旁路深链验收
status: Implemented
milestone: M154
lastUpdated: 2026-07-17
---

# M154 Admin 工作区预约上门/表单资料 → Task 旁路深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M154-01 | APPOINTMENTS_VISITS → Task | 预约 CONFIRMED 后加载区块可见「打开预约上门关联任务」；点击后 `GET /api/v1/tasks/{taskId}` 200 | `admin-pilot-smoke.spec.ts`（现场履约） |
| M154-02 | FORMS_EVIDENCE → Task | Task 完结后加载区块可见「打开表单资料关联任务」；点击后 Task GET 200（断言放在 complete 之后，避免打断双输入面板状态） | `admin-pilot-smoke.spec.ts`（完结） |
| M154-03 | 试点验收登记 | `ADMIN-PILOT-08AF` | `verify-admin-smoke.sh` |

## 明确不做

- Appointment / Visit / Form / Evidence 独立详情页；
- SavedView、inbound 列表、企业 OIDC。
