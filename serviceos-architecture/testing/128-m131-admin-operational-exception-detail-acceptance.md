---
title: M131 Admin 异常详情验收矩阵
status: Implemented
milestone: M131
---
| 编号 | 场景 | 预期 |
|---|---|---|
| M131-01 | 详情 | GET /operational-exceptions/{id} |
| M131-02 | acknowledge | If-Match + 幂等 |
| M131-03 | 深链 | 队列→详情 |
| M131-04 | 构建 | npm run build |
