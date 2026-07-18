---
title: M269 EXCLUSIVE_GATEWAY 运行时验收矩阵
status: Implemented
milestone: M269
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M269-01 | 唯一 true 出边 | 推进到命中任务 | ParserTest + Gateway IT |
| M269-02 | 零命中 | 失败关闭 | ParserTest |
| M269-03 | 多命中 | 失败关闭 | ParserTest |
| M269-04 | 线性回归 | 无条件推进不变 | Linear/Bootstrap IT |
| M269-05 | 上下文来源 | 使用工单冻结事实，不读最新配置 | Handler 代码 + IT |
