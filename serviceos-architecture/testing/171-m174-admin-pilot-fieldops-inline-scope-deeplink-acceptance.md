---
title: M174 Admin 现场/表单/SLA 事实格 scope 深链验收
status: Implemented
milestone: M174
lastUpdated: 2026-07-17
---

# M174 Admin 现场/表单/SLA 事实格 scope 深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M174-01 | 预约事实格 taskId → 任务详情 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M174-02 | 上门/联系/表单/资料/SLA/入站/审核事实格可点 | RouterLink 指向已有详情路由 | 页面代码审查 |
| M174-03 | 试点验收登记 | `ADMIN-PILOT-08FI` | `verify-admin-smoke.sh` |

## 明确不做

- ServiceNetwork/Technician 目录、FieldOperation、SavedView、ReviewRoute。
