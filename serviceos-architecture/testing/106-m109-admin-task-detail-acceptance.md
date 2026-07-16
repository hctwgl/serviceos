---
title: M109 Admin 任务详情验收矩阵
status: Implemented
milestone: M109
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M109-01 | 详情 | 调用 GET /tasks/{id} |
| M109-02 | Attempt | 调用 execution-attempts；HUMAN 可为空页 |
| M109-03 | 命令 | 复用 allowed-actions 面板 |
| M109-04 | 构建 | npm run build 通过 |
