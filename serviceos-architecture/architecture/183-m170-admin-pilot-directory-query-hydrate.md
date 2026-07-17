---
title: M170 Admin 目录页 route.query 水合
status: Implemented
milestone: M170
lastUpdated: 2026-07-17
---

# M170 Admin 目录页 route.query 水合

## 1. 范围

承接 M169 专项队列水合，将 Accepted OpenAPI 筛选接到目录页：

```text
/work-orders?status&clientCode&projectId
/tasks?status&taskKind&projectId&assignee
/sla?status&projectId
/projects?status&clientId&activeOn
```

不新增 OpenAPI、不做 SavedView。

## 2. 实现要点

1. 复用 `firstRouteQuery`；挂载时水合后查询；
2. 侧栏直达默认值不变（工单/任务不限，SLA=BREACHED，项目=ACTIVE）；
3. Playwright `ADMIN-PILOT-08DH`。

## 3. 事务 / 授权 / 幂等

只读 UI；列表授权仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `WorkOrderDirectoryPage.vue` / `TaskDirectoryPage.vue` / `SlaQueuePage.vue` /
  `ProjectDirectoryPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/167-m170-admin-pilot-directory-query-hydrate-acceptance.md`
