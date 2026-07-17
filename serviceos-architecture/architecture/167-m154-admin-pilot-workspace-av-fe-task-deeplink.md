---
title: M154 Admin 工作区预约上门/表单资料 → Task 旁路深链
status: Implemented
milestone: M154
lastUpdated: 2026-07-17
---

# M154 Admin 工作区预约上门/表单资料 → Task 旁路深链

## 1. 范围

承接 M153，在尚无独立 Admin 详情页的工作区区块上，补齐到已 Implemented Task 详情的运营旁路：

```text
工单工作区 APPOINTMENTS_VISITS → taskId → /tasks/{taskId}
工单工作区 FORMS_EVIDENCE → taskId → /tasks/{taskId}
```

复用 `ADMIN.TASK.DETAIL`（现场面板 / 表单资料面板）；不新增 OpenAPI，不发明 Appointment/Visit/Form/Evidence 详情页。

## 2. 实现要点

1. `APPOINTMENTS_VISITS`：优先 appointments，其次 visits / contactAttempts，按 `taskId` 去重；
2. `FORMS_EVIDENCE`：优先 formSubmissions，其次 forms / evidenceItems / evidenceSlots；
3. Playwright：预约确认后证明 AV 深链；Task 完结后证明 FE 深链（置于 complete 之后，避免打断双输入面板）。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；区块与 Task GET 仍由后端 Capability / Scope 强制；缺权数组为 null 时不渲染。

## 4. 明确未实现

- Appointment / Visit / FormSubmission / EvidenceItem 独立详情页（Visit 仍缺 GET by id）；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/151-m154-admin-pilot-workspace-av-fe-task-deeplink-acceptance.md`
- `ADMIN-PILOT-08AF`
