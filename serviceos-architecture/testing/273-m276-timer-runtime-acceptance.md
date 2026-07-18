---
title: M276 TIMER 运行时验收矩阵
status: Implemented
milestone: M276
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M276-01 | 进入 TIMER | WAITING 节点 + 订阅 | Timer IT |
| M276-02 | 到期点火 | 推进下一任务，订阅 FIRED | Timer IT |
| M276-03 | 无到期任务 | worker EMPTY | Timer IT |
| M276-04 | 发布缺 duration | 失败关闭 | Validator（形态） |
