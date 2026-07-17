---
title: M171 Admin 外发关联资源与回执入站交叉深链
status: Implemented
milestone: M171
lastUpdated: 2026-07-17
---

# M171 Admin 外发关联资源与回执入站交叉深链

## 1. 范围

承接 M147 / M163 / M168，补齐 Accepted 投影与详情字段上的交叉深链：

```text
外发详情 / 工作区 INTEGRATION
→ sourceTaskId / sourceSnapshotId / sourceReviewCaseId / clientReviewCaseId

外部审核回执
→ inboundEnvelopeId（及既有 Canonical）
```

不新增 OpenAPI；不发明 ReviewRoute 详情页。

## 2. 实现要点

1. `OutboundDelivery` 前端类型补齐 `sourceSnapshotId`；
2. 外发详情与工作区 `ob /` 关联资源链接；
3. 回执详情 → 入站 Envelope；
4. Playwright `ADMIN-PILOT-08OX`。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation / ReviewRoute 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `OutboundDeliveryDetailPage.vue` / `WorkOrderWorkspacePage.vue` /
  `ExternalReviewReceiptDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/168-m171-admin-pilot-outbound-related-resource-deeplink-acceptance.md`
