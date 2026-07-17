---
title: M175 Admin 运营异常 handlingTaskId 深链
status: Implemented
milestone: M175
lastUpdated: 2026-07-17
---

# M175 Admin 运营异常 handlingTaskId 深链

## 1. 范围

承接 M58 / M100 / M165 / M173，将运营异常详情中已 Implemented 的
`handlingTaskId` 升级为 `ADMIN.TASK.DETAIL` 深链：

```text
异常详情 → handlingTaskId → /tasks/{id}
```

`sourceType` / `sourceId` / `sourceAttemptId` 保持明文（OpenAPI 为自由字符串，
无封闭枚举到详情路由的映射）。

## 2. 实现要点

1. `ExceptionDetailPage` 事实格与「打开人工接管任务」链接；
2. Admin Pilot 种子补齐 OPEN 异常 + HUMAN 接管 Task；
3. Playwright `ADMIN-PILOT-08HT`：GET `/tasks/{handlingTaskId}` 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；异常 GET 走 `operations.exception.read` + 项目范围；
接管 Task 无 project 绑定时走 TENANT `task.read`（与生产 createHandlingTask 一致）。

## 4. 明确未实现

- 多态 sourceId 深链、FieldOperation、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `ExceptionDetailPage.vue`
- `seed-admin-pilot.sql`
- `admin-pilot-smoke.spec.ts`
- `testing/172-m175-admin-pilot-exception-handling-task-deeplink-acceptance.md`
