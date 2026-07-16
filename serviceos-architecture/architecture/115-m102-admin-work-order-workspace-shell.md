---
title: M102 Admin 工单工作区只读外壳
status: Implemented
milestone: M102
---

# M102 Admin 工单工作区只读外壳

## 1. 目标

在 `serviceos-admin-web` 窄交付工单工作区只读页，消费已 Accepted 的 API-06 workspace /
activity-summary / sections 查询。不新增后端契约，不实现命令 UI。

## 2. 交付

- 路由：`ADMIN.WORKORDER.LOOKUP`（`/work-orders`）、`ADMIN.WORKORDER.WORKSPACE`
  （`/work-orders/:id`）；
- 顶层工作区概览、当前任务/服务责任/SLA/异常摘要、最近活动；
- 按需加载 TASKS / TIMELINE_AUDIT / APPOINTMENTS_VISITS / FORMS_EVIDENCE /
  REVIEWS_CORRECTIONS / INTEGRATION；
- 外发队列提供工作区深链；
- `npm run build` 通过。

## 3. 明确未实现

命令/allowed-actions 渲染、SavedView、正式 OIDC、设计系统、Network/Technician、E2E。
