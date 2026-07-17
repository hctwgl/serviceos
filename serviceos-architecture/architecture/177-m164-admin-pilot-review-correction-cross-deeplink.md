---
title: M164 Admin 审核/整改详情交叉深链
status: Implemented
milestone: M164
lastUpdated: 2026-07-17
---

# M164 Admin 审核/整改详情交叉深链

## 1. 范围

承接 M163，在已有 ReviewCase / CorrectionCase / EvidenceSetSnapshot 详情页之间补齐交叉深链：

```text
审核详情 → 资料快照 / 源审核案例（重开后继）
整改详情 → 源资料快照 / 最近补传快照（及既有源审核/任务链）
```

不新增 OpenAPI 或后端契约。

## 2. 实现要点

1. `ReviewCaseDetailPage`：`evidenceSetSnapshotId`、`reopenedFromReviewCaseId` RouterLink；
2. `CorrectionCaseDetailPage`：展示并链到 `sourceEvidenceSetSnapshotId` / `latestResubmissionSnapshotId`；
3. Playwright `ADMIN-PILOT-08RC`：驳回链路快照 GET；重开后继 → 源审核 GET。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、异常摘要→队列 query 水合。

## 5. 证据入口

- `ReviewCaseDetailPage.vue` / `CorrectionCaseDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/161-m164-admin-pilot-review-correction-cross-deeplink-acceptance.md`
