---
title: M148 Admin 审核/整改队列筛选
status: Implemented
milestone: M148
lastUpdated: 2026-07-17
---

# M148 Admin 审核/整改队列筛选

## 1. 范围

承接 M147，将已 Accepted/Implemented 的 API-06 §6 审核/整改队列筛选接到 Admin：

```text
审核队列 → status / origin / projectId / taskId（默认 status=OPEN）
整改队列 → status / projectId / taskId / sourceReviewCaseId（默认 status=IN_PROGRESS）
```

不新增 OpenAPI、不支持多状态 OR、不做 SavedView。

## 2. 实现要点

1. `ReviewQueuePage` / `CorrectionQueuePage` 增加筛选表单，模式对齐 `OutboundQueuePage`；
2. `queues.ts` 参数类型化；整改客户端默认仍显式 `IN_PROGRESS`（服务端省略默认为 OPEN）；
3. Playwright：审核 `OPEN+taskId`；整改 `IN_PROGRESS+sourceReviewCaseId`。

## 3. 事务 / 授权 / 幂等

本切片为只读查询 UI：授权仍由后端队列 GET 的 Capability 与 Scope 强制；不改变 Case 状态机。

## 4. 明确未实现

- 多 status OR、SavedView、SLA/assignee 富化筛选；
- `sourceWorkOrderId`（契约未提供）；
- 专用入站队列列表 API、真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/ReviewQueuePage.vue`
- `serviceos-admin-web/src/pages/CorrectionQueuePage.vue`
- `serviceos-admin-web/src/api/queues.ts`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/145-m148-admin-review-correction-queue-filters-acceptance.md`
- `ADMIN-PILOT-08RQ` / `ADMIN-PILOT-08CQ`
