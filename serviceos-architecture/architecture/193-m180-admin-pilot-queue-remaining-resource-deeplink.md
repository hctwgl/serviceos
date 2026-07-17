---
title: M180 Admin 专项队列剩余关联资源深链
status: Implemented
milestone: M180
lastUpdated: 2026-07-17
---

# M180 Admin 专项队列剩余关联资源深链

## 1. 范围

承接 M176 / M179，补齐 Correction/Review 队列仍未深链的 Accepted OpenAPI 关联字段：

```text
整改队列 → projectId / taskId / latestResubmissionSnapshotId
审核队列 → evidenceSetSnapshotId / sourceReviewCaseId / reopenedFromReviewCaseId
```

前端队列类型对齐 Core OpenAPI `CorrectionCaseQueueItem` / `ReviewCaseQueueItem`。
不改 QueueTable 单元格渲染；不新增 OpenAPI。

## 2. 实现要点

1. `queues.ts` 补齐队列投影字段；
2. `CorrectionQueuePage` / `ReviewQueuePage` 关联资源条；
3. Playwright `ADMIN-PILOT-08QR`：审核队列 → Snapshot GET 200；整改队列 → Project/来源 Task GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- QueueTable 行内单元格链接、SavedView、FieldOperation、企业 OIDC/BFF。

## 5. 证据入口

- `queues.ts` / `CorrectionQueuePage.vue` / `ReviewQueuePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/177-m180-admin-pilot-queue-remaining-resource-deeplink-acceptance.md`
