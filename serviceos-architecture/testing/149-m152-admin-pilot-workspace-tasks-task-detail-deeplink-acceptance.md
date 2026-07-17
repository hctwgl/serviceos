---
title: M152 Admin 工作区 TASKS → 任务详情深链验收
status: Implemented
milestone: M152
lastUpdated: 2026-07-17
---

# M152 Admin 工作区 TASKS → 任务详情深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M152-01 | Workspace TASKS → Task 详情 | 按需加载 `TASKS` 后可见「打开区块任务」；点击 Pilot 任务链接触发 `GET /api/v1/tasks/{taskId}` 200，进入任务详情 | `admin-pilot-smoke.spec.ts` |
| M152-02 | 试点验收登记 | `ADMIN-PILOT-08TD` | `verify-admin-smoke.sh` |

## 明确不做

- TIMELINE / 预约上门 / 表单证据区块的独立详情深链；
- 任务命令面扩展、SavedView、inbound 列表、企业 OIDC。
