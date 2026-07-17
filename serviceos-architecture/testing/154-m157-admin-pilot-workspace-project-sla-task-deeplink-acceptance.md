---
title: M157 Admin 工作区项目与 SLA 任务深链验收
status: Implemented
milestone: M157
lastUpdated: 2026-07-17
---

# M157 Admin 工作区项目与 SLA 任务深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M157-01 | Workspace → 项目详情 | 概览项目链接触发 `GET /api/v1/projects/{projectId}` 200 | `admin-pilot-smoke.spec.ts` |
| M157-02 | Workspace SLA → Task | 「打开 SLA 关联任务」触发 Task GET 200 | `admin-pilot-smoke.spec.ts` |
| M157-03 | SLA 工作台 → Task | RUNNING+projectId 筛选后「打开关联任务」触发 Task GET 200 | `admin-pilot-smoke.spec.ts` |
| M157-04 | 试点验收登记 | `ADMIN-PILOT-08XN` | `verify-admin-smoke.sh` |

## 明确不做

- Visit 详情、inbound 列表、SavedView、企业 OIDC。
