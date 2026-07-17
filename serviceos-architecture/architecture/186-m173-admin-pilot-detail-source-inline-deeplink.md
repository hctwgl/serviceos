---
title: M173 Admin 详情页源资源明文字段深链
status: Implemented
milestone: M173
lastUpdated: 2026-07-17
---

# M173 Admin 详情页源资源明文字段深链

## 1. 范围

承接 M164 / M171 / M172，将详情事实格中仍以明文展示、且已有 Implemented 详情契约的
源资源 UUID 升级为 RouterLink：

```text
外发详情 → sourceWorkOrder / sourceReview / sourceTask / sourceSnapshot / clientReview
外部审核回执 → inboundEnvelope / canonical / coordinationTask
异常详情 → workOrder / task
审核详情 → task / snapshot
整改详情 → task / correctionTask / sourceReview / sourceSnapshot / latestResubmitSnapshot
```

`reviewRouteId`、多态 `sourceId`/`sourceAttemptId` 保持明文（无安全详情契约）。

## 2. 实现要点

1. 复用已有 Admin 路由与 GET；不新增 OpenAPI；
2. 与下方「打开…」链接行并存，事实格本身可点；
3. Playwright `ADMIN-PILOT-08OI`：外发事实格 `sourceTaskId` → Task GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- ReviewRoute / FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `OutboundDeliveryDetailPage.vue` / `ExternalReviewReceiptDetailPage.vue` /
  `ExceptionDetailPage.vue` / `ReviewCaseDetailPage.vue` / `CorrectionCaseDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/170-m173-admin-pilot-detail-source-inline-deeplink-acceptance.md`
