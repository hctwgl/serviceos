---
title: M449 Admin 工单目录当前任务列验收矩阵
version: 0.1.0
status: Implemented
milestone: M449
lastUpdated: 2026-07-21
---

# M449 Admin 工单目录当前任务列验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 有 ACTIVE 任务 | list/detail 暴露 currentTaskType | `listAndDetailExposeCurrentStageCodeFromActiveTask` |
| A2 | MVC | items[].currentTaskType 绑定 | `WorkOrderControllerSecurityTest` |
| A3 | Admin | 列可见且中文标签 | Playwright |

产品状态：`READY_FOR_REVIEW`。
