---
title: M103 Admin 工作区 allowed-actions 只读投影
status: Implemented
milestone: M103
---

# M103 Admin 工作区 allowed-actions 只读投影

## 1. 目标

在 Admin 工单工作区展示当前任务的服务端 `GET /tasks/{id}/allowed-actions` 投影。
本里程碑只读展示，不渲染命令表单、不本地推导动作。

## 2. 交付

- 工作区加载当前任务后请求 allowed-actions；
- 展示 code/label/obligations 与 asOf/resourceVersion；
- `npm run build` 通过。

## 3. 明确未实现

命令执行 UI、表单输入、乐观更新、正式 OIDC、SavedView、E2E。
