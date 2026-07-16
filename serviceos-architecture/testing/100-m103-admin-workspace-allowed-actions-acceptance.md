---
title: M103 Admin 工作区 allowed-actions 只读投影验收矩阵
status: Implemented
milestone: M103
---

# M103 Admin 工作区 allowed-actions 只读投影验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M103-01 | 有当前任务 | 调用 `/tasks/{id}/allowed-actions` 并展示动作列表 |
| M103-02 | 无当前任务 | 显示无当前任务，不伪造动作 |
| M103-03 | 读取失败 | 显示错误，不本地补默认动作 |
| M103-04 | 构建 | `npm run build` 通过 |
