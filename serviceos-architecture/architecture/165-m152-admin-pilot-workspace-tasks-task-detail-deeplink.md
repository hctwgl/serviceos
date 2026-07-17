---
title: M152 Admin 工作区 TASKS → 任务详情深链
status: Implemented
milestone: M152
lastUpdated: 2026-07-17
---

# M152 Admin 工作区 TASKS → 任务详情深链

## 1. 范围

承接 M151，在已 Implemented 的工作区 `TASKS` 投影上补齐与 M149 对称的运营深链：

```text
工单工作区 TASKS
→ items[] → /tasks/{taskId}
```

复用已有 `ADMIN.TASK.DETAIL` 与 `GET /api/v1/tasks/{taskId}`；不新增 OpenAPI，不改变 Task 状态机。

## 2. 实现要点

1. `WorkOrderWorkspacePage` 解析 `sectionData.tasks.items[]` 生成「打开区块任务」`RouterLink`；
2. 文案为 `taskType / taskKind / status / taskId`，与权威区「打开任务」区分；
3. Playwright：固定 Pilot 工单加载 `TASKS` 后点击深链，断言详情 GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；区块与详情 GET 仍由后端 Capability / Scope 强制；缺权或空列表时不渲染链接。

## 4. 明确未实现

- TIMELINE / APPOINTMENTS_VISITS / FORMS_EVIDENCE 独立详情深链；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/149-m152-admin-pilot-workspace-tasks-task-detail-deeplink-acceptance.md`
- `ADMIN-PILOT-08TD`
