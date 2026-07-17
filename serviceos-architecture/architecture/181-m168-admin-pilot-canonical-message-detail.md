---
title: M168 Admin Canonical Message 独立详情页
status: Implemented
milestone: M168
lastUpdated: 2026-07-17
---

# M168 Admin Canonical Message 独立详情页

## 1. 范围

承接 M145 / M167，为已 Implemented `GET /canonical-messages/{id}`（`integration.readInbound`）补齐独立 Admin 详情页与深链：

```text
入站 Envelope / 工作区 INTEGRATION / 外部审核回执
→ /integration/canonical/{canonicalMessageId}
→ GET /api/v1/canonical-messages/{id}
```

不新增 OpenAPI、不暴露含 PII 的标准消息正文。

## 2. 实现要点

1. `CanonicalMessageDetailPage` + 路由 `ADMIN.INTEGRATION.CANONICAL.DETAIL`；
2. Envelope / ExternalReviewReceipt / 工作区 INTEGRATION 深链；
3. Playwright `ADMIN-PILOT-08CM`：Envelope → 独立页；INTEGRATION → 独立页。

## 3. 事务 / 授权 / 幂等

只读 UI；授权仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、原文下载、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `CanonicalMessageDetailPage.vue`
- `InboundEnvelopeDetailPage.vue` / `WorkOrderWorkspacePage.vue` / `ExternalReviewReceiptDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/165-m168-admin-pilot-canonical-message-detail-acceptance.md`
