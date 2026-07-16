---
title: M111 Admin 运营异常确认验收矩阵
status: Implemented
milestone: M111
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M111-01 | 按钮 | 仅 OPEN 且 allowedActions 含 ACKNOWLEDGE |
| M111-02 | 命令 | Idempotency-Key + If-Match + 可选 note |
| M111-03 | 成功 | 刷新队列 |
| M111-04 | 构建 | npm run build 通过 |
