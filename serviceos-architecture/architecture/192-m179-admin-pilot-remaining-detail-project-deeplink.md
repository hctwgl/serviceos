---
title: M179 Admin 剩余详情页 projectId 深链
status: Implemented
milestone: M179
lastUpdated: 2026-07-17
---

# M179 Admin 剩余详情页 projectId 深链

## 1. 范围

承接 M172 / M174 / M178，将仍未深链的详情页 Accepted `projectId` 升级为
`ADMIN.PROJECT.DETAIL` RouterLink：

```text
整改 / 表单提交 / 资料项 / 资料快照 / 预约 / 上门 / SLA 实例 / 任务 /
入站 Envelope / 外部审核回执
→ projectId → /projects/{id}
```

附带入站事实格 `canonicalMessageId`、回执事实格 `reviewCaseId`（均已有 Implemented 详情路由）。
不新增 OpenAPI；不改 QueueTable 单元格渲染。

## 2. 实现要点

1. 事实格 `projectId` + 「打开项目」交叉链接条（可空字段条件渲染）；
2. Playwright `ADMIN-PILOT-08PP`：整改详情 → GET Project 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；项目 GET 仍由 `project.read` 与 Tenant/Project Scope 强制。

## 4. 明确未实现

- QueueTable 行内单元格链接、FieldOperation / ReviewRoute / SavedView、
  ServiceNetwork/Technician 目录、企业 OIDC/BFF。

## 5. 证据入口

- `CorrectionCaseDetailPage.vue` / `FormSubmissionDetailPage.vue` /
  `EvidenceItemDetailPage.vue` / `EvidenceSetSnapshotDetailPage.vue` /
  `AppointmentDetailPage.vue` / `VisitDetailPage.vue` /
  `SlaInstanceDetailPage.vue` / `TaskDetailPage.vue` /
  `InboundEnvelopeDetailPage.vue` / `ExternalReviewReceiptDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/176-m179-admin-pilot-remaining-detail-project-deeplink-acceptance.md`
