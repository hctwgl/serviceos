---
title: M176 Admin 专项队列关联资源深链
status: Implemented
milestone: M176
lastUpdated: 2026-07-17
---

# M176 Admin 专项队列关联资源深链

## 1. 范围

承接 M148 / M169 / M175，在专项队列列表下方补齐 Accepted 投影字段上的关联资源深链：

```text
整改队列 → sourceReviewCaseId / correctionTaskId
审核队列 → projectId / taskId
```

不改 `QueueTable` 单元格渲染；不新增 OpenAPI。

## 2. 实现要点

1. `CorrectionQueuePage`：`correction-queue-cross-links`；
2. `ReviewQueuePage`：`review-queue-cross-links`；
3. Playwright `ADMIN-PILOT-08QC`：整改队列 → 源审核 / 整改 Task GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- QueueTable 行内单元格链接、SavedView、FieldOperation、企业 OIDC/BFF。

## 5. 证据入口

- `CorrectionQueuePage.vue` / `ReviewQueuePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/173-m176-admin-pilot-queue-related-resource-deeplink-acceptance.md`
