---
title: M124 Admin 签到签退验收矩阵
status: Implemented
milestone: M124
---
| 编号 | 场景 | 预期 |
|---|---|---|
| M124-01 | check-in | Idempotency-Key=deviceCommandId |
| M124-02 | check-out | If-Match aggregateVersion |
| M124-03 | Visit 列表 | workOrder visits |
| M124-04 | 构建 | npm run build |
