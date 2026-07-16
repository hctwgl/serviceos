---
title: M105 Admin 人工任务命令面板
status: Implemented
milestone: M105
---

# M105 Admin 人工任务命令面板

## 1. 目标

在 Admin 工单工作区基于服务端 `allowed-actions` 执行 claim/start/complete/release。
前端不本地推导动作；命令必须携带 Idempotency-Key 与 If-Match。

## 2. 交付

- `apiPost` 支持幂等键与版本匹配；
- 工作区命令面板仅渲染服务端返回动作；
- complete/release 按契约收集必填输入；
- 成功后刷新工作区；`npm run build` 通过。

## 3. 明确未实现

表单/资料提交流程编排、OIDC SDK、SavedView、设计系统、E2E、Network/Technician。
