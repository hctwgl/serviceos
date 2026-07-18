---
title: M280 取消时配置化补偿任务验收矩阵
status: Implemented
milestone: M280
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M280-01 | 完成带补偿节点后取消 | 创建补偿请求与 PENDING 补偿任务 | PASS `WorkflowCompensationOnCancelPostgresIT` |
| M280-02 | 无补偿声明的未完成节点 | 不创建补偿 | PASS 同 IT（INSTALL 未完成） |
| M280-03 | M279 取消回归 | 仍通过 | PASS `WorkflowCancelReopenJumpPostgresIT` |
