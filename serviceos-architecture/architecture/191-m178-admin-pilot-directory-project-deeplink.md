---
title: M178 Admin 目录/SLA 项目关联深链
status: Implemented
milestone: M178
lastUpdated: 2026-07-17
---

# M178 Admin 目录/SLA 项目关联深链

## 1. 范围

承接 M151 / M157 / M177，在工单/任务目录与 SLA 工作台补齐 `projectId` 关联深链：

```text
工单目录 / 任务目录 / SLA 工作台 → projectId → /projects/{id}
```

不改 QueueTable 单元格渲染；不新增 OpenAPI。

## 2. 实现要点

1. `WorkOrderDirectoryPage` / `TaskDirectoryPage` / `SlaQueuePage` 关联资源条；
2. Playwright `ADMIN-PILOT-08DP`：目录筛选后 GET Project 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；项目 GET 仍由 `project.read` 与 Scope 强制。

## 4. 明确未实现

- QueueTable 行内单元格链接、SavedView、FieldOperation、企业 OIDC/BFF。

## 5. 证据入口

- `WorkOrderDirectoryPage.vue` / `TaskDirectoryPage.vue` / `SlaQueuePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/175-m178-admin-pilot-directory-project-deeplink-acceptance.md`
