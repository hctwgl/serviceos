---
title: M155 Admin 预约/表单提交详情页
status: Implemented
milestone: M155
lastUpdated: 2026-07-17
---

# M155 Admin 预约/表单提交详情页

## 1. 范围

承接 M154，为已有 GET 契约补齐 Admin 只读详情页，并从工作区深链进入：

```text
APPOINTMENTS_VISITS.appointments[] → /appointments/{appointmentId}
  → GET /api/v1/appointments/{appointmentId}

FORMS_EVIDENCE.formSubmissions[] → /form-submissions/{submissionId}
  → GET /api/v1/form-submissions/{submissionId}
```

不新增 OpenAPI；不实现 Visit 详情（仍缺 GET by id）；写操作仍留在 Task 面板。

## 2. 实现要点

1. `AppointmentDetailPage` / `FormSubmissionDetailPage` + 路由名
   `ADMIN.APPOINTMENT.DETAIL` / `ADMIN.FORM_SUBMISSION.DETAIL`；
2. 工作区「打开预约详情」「打开表单提交详情」；保留 M154 Task 旁路；
3. Playwright：预约确认后证明 Appointment GET；完结后证明 FormSubmission GET。

## 3. 事务 / 授权 / 幂等

只读 UI；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- Visit / ContactAttempt / EvidenceItem 独立详情页；
- 预约/表单写命令迁出 Task 面板；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/AppointmentDetailPage.vue`
- `serviceos-admin-web/src/pages/FormSubmissionDetailPage.vue`
- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/152-m155-admin-pilot-appointment-form-submission-detail-acceptance.md`
- `ADMIN-PILOT-08AD`
