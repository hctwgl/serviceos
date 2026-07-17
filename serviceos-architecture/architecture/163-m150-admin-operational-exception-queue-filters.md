---
title: M150 Admin 运营异常队列筛选
status: Implemented
milestone: M150
lastUpdated: 2026-07-17
---

# M150 Admin 运营异常队列筛选

## 1. 范围

承接 M149，将已 Accepted/Implemented 的 API-06 §6 `GET /operational-exceptions` 筛选接到 Admin：

```text
运营异常队列
→ status / severity / category / projectId / workOrderId / taskId
→ 查询（默认 status=OPEN；省略 status 表示不限）
```

不新增 OpenAPI、不支持多状态 OR、不做 SavedView。

## 2. 实现要点

1. `ExceptionQueuePage` 增加筛选表单，模式对齐 `OutboundQueuePage`；
2. `queues.ts` 参数类型化为 `OperationalExceptionQueueQuery`；
3. Playwright：默认 OPEN，切换 `ACKNOWLEDGED+P1` 查询返回 200。

## 3. 事务 / 授权 / 幂等

只读查询 UI；授权仍由后端 Capability 与 Scope 强制；确认（acknowledge）写路径未改。

## 4. 明确未实现

- 多 status OR、SavedView；
- 专用入站队列列表 API、企业 OIDC/BFF；
- 真实 sandbox、人工标记已送达/放弃。

## 5. 证据入口

- `serviceos-admin-web/src/pages/ExceptionQueuePage.vue`
- `serviceos-admin-web/src/api/queues.ts`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/147-m150-admin-operational-exception-queue-filters-acceptance.md`
- `ADMIN-PILOT-08EQ`
