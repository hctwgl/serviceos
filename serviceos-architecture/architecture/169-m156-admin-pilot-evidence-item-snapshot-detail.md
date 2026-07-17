---
title: M156 Admin 资料项/资料快照详情页
status: Implemented
milestone: M156
lastUpdated: 2026-07-17
---

# M156 Admin 资料项/资料快照详情页

## 1. 范围

承接 M155，为已有 GET 契约补齐 Admin 只读详情页：

```text
FORMS_EVIDENCE.evidenceItems[] → /evidence-items/{evidenceItemId}
  → GET /api/v1/evidence-items/{id}

Task 面板 createEvidenceSetSnapshot 后 → /evidence-set-snapshots/{id}
  → GET /api/v1/evidence-set-snapshots/{id}
```

不新增 OpenAPI；不实现 Visit 详情；写操作仍留在 Task 面板。

## 2. 实现要点

1. `EvidenceItemDetailPage` / `EvidenceSetSnapshotDetailPage` + 路由名
   `ADMIN.EVIDENCE_ITEM.DETAIL` / `ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL`；
2. 工作区「打开资料项详情」；Task 面板「打开资料快照」；
3. Playwright：快照创建后新页签证明 Snapshot GET；完结后工作区证明 Item GET。

## 3. 事务 / 授权 / 幂等

只读 UI；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- Visit / ContactAttempt 独立详情页；
- EvidenceSlot / FormVersion 独立详情页；
- 详情页写命令、下载授权 UI 迁出 Task 面板；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/EvidenceItemDetailPage.vue`
- `serviceos-admin-web/src/pages/EvidenceSetSnapshotDetailPage.vue`
- `serviceos-admin-web/src/components/TaskFormsEvidencePanel.vue`
- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/153-m156-admin-pilot-evidence-item-snapshot-detail-acceptance.md`
- `ADMIN-PILOT-08ED`
