---
title: M101 Admin Portal 队列外壳验收矩阵
status: Implemented
milestone: M101
---

# M101 Admin Portal 队列外壳验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M101-01 | 路由 | `/reviews` `/corrections` `/integration/outbound` `/exceptions` 可到达 |
| M101-02 | 鉴权头 | 请求携带 Authorization（若已保存）与 X-Correlation-Id |
| M101-03 | 构建 | `npm run build` 通过 |
| M101-04 | 边界 | 无前端伪造 tenant/capability；无命令写操作 UI |
