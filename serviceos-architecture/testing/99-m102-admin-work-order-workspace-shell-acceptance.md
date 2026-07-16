---
title: M102 Admin 工单工作区只读外壳验收矩阵
status: Implemented
milestone: M102
---

# M102 Admin 工单工作区只读外壳验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M102-01 | 查找页 | `/work-orders` 接受 UUID 并跳转工作区 |
| M102-02 | 工作区顶层 | 调用 workspace + activity-summary；展示 freshness/asOf |
| M102-03 | 区块按需 | 六个 Accepted section 可切换加载；UNAVAILABLE 禁用 |
| M102-04 | 深链 | 外发队列可链到 `/work-orders/{sourceWorkOrderId}` |
| M102-05 | 构建 | `npm run build` 通过；无前端写命令 |
