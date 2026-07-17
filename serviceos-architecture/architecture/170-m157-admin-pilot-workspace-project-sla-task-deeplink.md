---
title: M157 Admin 工作区项目与 SLA 任务深链
status: Implemented
milestone: M157
lastUpdated: 2026-07-17
---

# M157 Admin 工作区项目与 SLA 任务深链

## 1. 范围

承接 M156，补齐工作区/SLA 上仍为纯文本的已有资源出口：

```text
工作区概览 projectId → /projects/{projectId}
工作区 currentTaskSummary.taskId → /tasks/{taskId}
工作区 SLA 实例 taskId → /tasks/{taskId}
SLA 工作台 taskId → /tasks/{taskId}
```

复用已 Implemented 项目详情与任务详情；不新增 OpenAPI。

## 2. 实现要点

1. `WorkOrderWorkspacePage`：项目与当前任务 `RouterLink`；「打开 SLA 关联任务」；
2. `SlaQueuePage`：列展示 `taskId`；「打开关联任务」；
3. Playwright：工作区项目 GET；工作区/队列 SLA→Task GET。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- Visit 详情页（缺 GET by id）；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- ServiceNetwork、评分派单；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/src/pages/SlaQueuePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/154-m157-admin-pilot-workspace-project-sla-task-deeplink-acceptance.md`
- `ADMIN-PILOT-08XN`
