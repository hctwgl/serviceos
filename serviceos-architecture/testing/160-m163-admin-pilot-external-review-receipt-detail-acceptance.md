---
title: M163 Admin 外部审核回执详情页验收
status: Implemented
milestone: M163
lastUpdated: 2026-07-17
---

# M163 Admin 外部审核回执详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M163-01 | Admin 详情页 | `/external-review-receipts/{id}` 读取已有 GET | `ExternalReviewReceiptDetailPage.vue` |
| M163-02 | 时间线白名单 | ExternalReviewReceipt 可链 | `WorkOrderWorkspacePage.vue` |
| M163-03 | 厂端回调后深链 | 核心时间线 → 回执 GET 200 / APPROVED | `admin-pilot-smoke.spec.ts` |
| M163-04 | 试点验收登记 | `ADMIN-PILOT-08ER` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork、公开非 `/internal/` 新路径。
