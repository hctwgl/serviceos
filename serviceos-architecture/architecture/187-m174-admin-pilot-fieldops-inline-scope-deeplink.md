---
title: M174 Admin 现场/表单/SLA 事实格 scope 深链
status: Implemented
milestone: M174
lastUpdated: 2026-07-17
---

# M174 Admin 现场/表单/SLA 事实格 scope 深链

## 1. 范围

承接 M172 / M173，将现场履约、表单资料、SLA、任务与入站结果事实格中已有
Implemented 详情契约、但未在事实格展示的 scope / 源资源 ID 升级为可点字段：

```text
预约 / 上门 / 联系 → workOrderId / taskId（上门含 appointmentId；联系含 projectId）
表单提交 / 资料项 / 资料快照 → taskId
SLA 实例 → workOrderId / taskId
任务详情 → workOrderId
入站 Envelope / Canonical → resultId（仅 resultType=WORK_ORDER）
审核 → reopenedFromReviewCaseId
```

`technicianId` / `networkId` / `formVersionId` / `evidenceSlotId` 等无详情契约字段保持明文。

## 2. 实现要点

1. 复用已有 Admin 路由与 GET；不新增 OpenAPI；
2. Playwright `ADMIN-PILOT-08FI`：预约事实格 `taskId` → Task GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- ServiceNetwork / Technician 目录详情、FieldOperation、SavedView、企业 OIDC/BFF。

## 5. 证据入口

- `AppointmentDetailPage.vue` / `VisitDetailPage.vue` / `ContactAttemptDetailPage.vue` /
  `FormSubmissionDetailPage.vue` / `EvidenceItemDetailPage.vue` /
  `EvidenceSetSnapshotDetailPage.vue` / `SlaInstanceDetailPage.vue` /
  `TaskDetailPage.vue` / `InboundEnvelopeDetailPage.vue` /
  `CanonicalMessageDetailPage.vue` / `ReviewCaseDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/171-m174-admin-pilot-fieldops-inline-scope-deeplink-acceptance.md`
