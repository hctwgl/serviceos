---
title: M312 Workflow 画布属性面板验收矩阵
status: Implemented
milestone: M312
lastUpdated: 2026-07-19
---

# M312 Workflow 画布属性面板验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| W312-01 | SERVICE_TASK 缺 stage/taskType | 校验错误 | `workflowCanvasModel.test.mjs` |
| W312-02 | 完整任务节点 | 无错误 | 同上 |
| W312-03 | WAIT_EVENT 缺 waitEventType | 错误 | 同上 |
| W312-04 | nextNodeId 递增 | SERVICE_TASK_2 | 同上 |
| W312-05 | Admin build | vue-tsc + vite 成功 | `npm run build` |
| W312-06 | 添加 WAIT_EVENT + 改名 + undo/redo | JSON 往返 | Playwright `M312 节点属性面板与撤销重做` |
