---
title: M270 WAIT_EVENT 运行时验收矩阵
status: Implemented
milestone: M270
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M270-01 | 进入 WAIT_EVENT | 创建 WAITING 节点与订阅，不创建 Task | WaitEvent IT |
| M270-02 | 匹配信号唤醒 | 完成等待并推进下一任务 | WaitEvent IT |
| M270-03 | 完成后重复信号 | 不二次推进，replay=true | WaitEvent IT |
| M270-04 | 错误关联键 | 失败关闭 not found | WaitEvent IT |
| M270-05 | 发布缺 waitEventType | 配置发布失败关闭 | ValidatorTest |
