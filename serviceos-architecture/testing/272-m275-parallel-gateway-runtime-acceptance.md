---
title: M275 PARALLEL_GATEWAY 运行时验收矩阵
status: Implemented
milestone: M275
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M275-01 | Fork 激活 | 两条分支任务同时 ACTIVE | ParallelGateway IT |
| M275-02 | Join 未到齐 | 第一条完成不推进汇合后继 | ParallelGateway IT |
| M275-03 | Join 到齐 | 第二条完成后推进下一任务 | ParallelGateway IT |
| M275-04 | 重复 token | 同一 from 再次到达失败关闭 | Parser/计数唯一约束 |
| M275-05 | 发布形态非法 | 条件出边等失败关闭 | ValidatorTest |
