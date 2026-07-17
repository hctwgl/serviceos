---
title: M163 Admin 外部审核回执 ExternalReviewReceipt 详情页
status: Implemented
milestone: M163
lastUpdated: 2026-07-17
---

# M163 Admin 外部审核回执 ExternalReviewReceipt 详情页

## 1. 范围

承接 M162，为已 Implemented 的
`GET /api/v1/internal/external-review-receipts/{receiptId}`（`evidence.read`）补齐 Admin 只读详情，
并纳入时间线/最近活动白名单：

```text
GET /internal/external-review-receipts/{receiptId} → ExternalReviewReceipt
Admin /external-review-receipts/{id}
核心时间线 / TIMELINE_AUDIT / 最近活动 → ExternalReviewReceipt 深链
```

不新增 OpenAPI 字段语义；不把 `/internal/` 迁出为新公开路径。

## 2. 实现要点

1. Admin `ExternalReviewReceiptDetailPage` + `getExternalReviewReceipt`；
2. `TIMELINE_RESOURCE_ROUTES.ExternalReviewReceipt`；
3. Playwright `ADMIN-PILOT-08ER`：厂端回调后核心时间线 → 回执 GET 200。

## 3. 事务 / 授权 / 幂等

只读；授权复用 `evidence.read` + 实时 Project Scope。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、ServiceNetwork。

## 5. 证据入口

- `ExternalReviewReceiptController#get` / `DefaultExternalReviewReceiptService#get`
- `ExternalReviewReceiptDetailPage.vue` + `admin-pilot-smoke.spec.ts`
- `testing/160-m163-admin-pilot-external-review-receipt-detail-acceptance.md`
