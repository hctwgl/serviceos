---
title: M279 工单取消/重开与流程人工跳转验收矩阵
status: Implemented
milestone: M279
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M279-01 | 取消 ACTIVE 工单 | WO CANCELLED；根流程 CANCELLED；开放任务关闭 | PASS `WorkflowCancelReopenJumpPostgresIT` |
| M279-02 | 非法跳转目标 | 失败关闭 | PASS `jumpUnknownNodeFailsClosed` |
| M279-03 | 重开 CANCELLED | WO ACTIVE；新根流程启动首任务 | PASS 同 IT |
| M279-04 | 人工跳转 | 当前节点/任务取消；目标任务 ACTIVE | PASS 同 IT |
| M279-05 | 事件契约 | cancelled/reopened Schema 样本通过 | PASS `ContractValidationTest` |
