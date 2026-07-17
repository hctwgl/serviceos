---
title: M169 Admin 专项队列 route.query 水合
status: Implemented
milestone: M169
lastUpdated: 2026-07-17
---

# M169 Admin 专项队列 route.query 水合

## 1. 范围

承接 M165 Exception 队列水合模式，将 Accepted OpenAPI 筛选接到其余专项队列：

```text
/reviews?status&origin&projectId&taskId
/corrections?status&projectId&taskId&sourceReviewCaseId
/integration/inbound?processingStatus&messageType&projectId&…
/integration/outbound?status&businessMessageType&projectId&…
```

不新增 OpenAPI、不做 SavedView、不改侧栏直达默认值。

## 2. 实现要点

1. 抽取 `firstRouteQuery`；Exception 队列改用同一工具；
2. Review / Correction / Inbound / Outbound 挂载时水合后查询；
3. Playwright `ADMIN-PILOT-08QH`：四队列 deep-link GET 200 且表单已水合。

## 3. 事务 / 授权 / 幂等

只读 UI；列表授权仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、多 status OR、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `routeQuery.ts` / `ReviewQueuePage.vue` / `CorrectionQueuePage.vue` /
  `InboundEnvelopeQueuePage.vue` / `OutboundQueuePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/166-m169-admin-pilot-queue-query-hydrate-acceptance.md`
