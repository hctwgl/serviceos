---
title: M106 Admin 授权任务目录
status: Implemented
milestone: M106
---

# M106 Admin 授权任务目录

## 1. 目标

Admin 只读消费已实现的 `GET /tasks` 授权任务目录。

## 2. 交付

- 路由 `ADMIN.TASK.QUEUE`（`/tasks`）；
- status/taskKind/assignee=me 受控筛选；
- 有 workOrderId 时深链工作区；`npm run build` 通过。

## 3. 明确未实现

任务详情独立页、SavedView、OIDC SDK、E2E。
