---
title: M151 Admin 目录与 SLA 筛选补齐验收
status: Implemented
lastUpdated: 2026-07-17
---

# M151 Admin 目录与 SLA 筛选补齐验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M151-01 | 工单 projectId | GET `/work-orders?projectId=` 200 | PASS |
| M151-02 | 任务 projectId + SUCCEEDED | GET `/tasks` 两参数 200 | PASS |
| M151-03 | SLA projectId | GET `/sla-instances?projectId=` 200 | PASS |
| M151-04 | 项目 activeOn | GET `/projects?activeOn=` 200 | PASS |
| M151-05 | 试点验收登记 | `ADMIN-PILOT-08DF` | PASS |

不证明入站队列列表、SavedView 或真实 sandbox。
