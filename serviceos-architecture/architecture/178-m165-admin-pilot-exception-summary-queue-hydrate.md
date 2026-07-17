---
title: M165 Admin 工作区异常摘要 → 异常队列 query 水合
status: Implemented
milestone: M165
lastUpdated: 2026-07-17
---

# M165 Admin 工作区异常摘要 → 异常队列 query 水合

## 1. 范围

承接 M164 / M150，将工作区 `exceptionSummary` 接到已 Implemented 的运营异常队列：

```text
工单工作区 exceptionSummary
→ /exceptions?workOrderId={id}&status=OPEN
→ ExceptionQueuePage 从 route.query 水合筛选并查询
```

不新增 OpenAPI、不改异常摘要投影语义、不做 SavedView。

## 2. 实现要点

1. `WorkOrderWorkspacePage`：有 `exceptionSummary` 时展示「打开运营异常队列」深链；
2. `ExceptionQueuePage`：挂载时从 `route.query` 水合 status/severity/category/projectId/workOrderId/taskId；
3. 侧栏直达仍保持默认 `status=OPEN`、空 workOrderId（仅显式 query 覆盖）；
4. Playwright `ADMIN-PILOT-08EH`：固定 Pilot 工单 → 队列 GET `workOrderId+OPEN` 200 且表单已水合。

## 3. 事务 / 授权 / 幂等

只读 UI 深链与筛选水合；列表授权仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、多 status OR、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `WorkOrderWorkspacePage.vue` / `ExceptionQueuePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/162-m165-admin-pilot-exception-summary-queue-hydrate-acceptance.md`
