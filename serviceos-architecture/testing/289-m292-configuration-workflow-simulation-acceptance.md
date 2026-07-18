---
title: M292 WORKFLOW 配置干跑模拟验收矩阵
status: Implemented
milestone: M292
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M292-01 | 线性 WORKFLOW | 步骤含 START→TASK→END，outcome COMPLETED |
| M292-02 | EXCLUSIVE 单命中 | 进入正确分支 |
| M292-03 | EXCLUSIVE 零命中 | FAIL_CLOSED |
| M292-04 | WAIT_EVENT | outcome WAITING，不伪造信号 |
