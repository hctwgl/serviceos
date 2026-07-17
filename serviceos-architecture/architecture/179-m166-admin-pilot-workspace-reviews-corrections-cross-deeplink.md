---
title: M166 Admin 工作区审核/整改关联资源深链
status: Implemented
milestone: M166
lastUpdated: 2026-07-17
---

# M166 Admin 工作区审核/整改关联资源深链

## 1. 范围

承接 M149 / M164，在工作区 `REVIEWS_CORRECTIONS` 区块直接暴露 Accepted 投影中的关联资源深链：

```text
REVIEWS_CORRECTIONS
→ evidenceSetSnapshotId / reopenedFromReviewCaseId
→ sourceReviewCaseId / latestResubmissionSnapshotId
→ 已有 Snapshot / Review 详情页
```

不新增 OpenAPI；不猜测 Correction 源快照字段（工作区摘要无 `sourceEvidenceSetSnapshotId`）。

## 2. 实现要点

1. `WorkOrderWorkspacePage` 收集区块交叉链接，前缀 `rc /` 避免严格模式冲突；
2. Playwright：驳回链路证明 Snapshot GET；重开链路证明源 Review GET。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `WorkOrderWorkspacePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/163-m166-admin-pilot-workspace-reviews-corrections-cross-deeplink-acceptance.md`
