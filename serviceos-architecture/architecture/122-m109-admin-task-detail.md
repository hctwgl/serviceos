---
title: M109 Admin 任务详情
status: Implemented
milestone: M109
---

# M109 Admin 任务详情

## 1. 目标

Admin 消费 `GET /tasks/{id}` 与执行 Attempt 历史，并复用 allowed-actions 命令面板。

## 2. 交付

- 路由 `ADMIN.TASK.DETAIL`（`/tasks/:id`）；
- 展示任务事实、命令面板、Attempt 历史；
- 任务目录深链；`npm run build` 通过。

## 3. 明确未实现

表单/资料提交流程编排、OIDC SDK、SavedView、E2E。
