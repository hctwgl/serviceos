---
title: M47 CorrectionCase 整改 Task 自动创建
version: 0.1.0
status: Implemented
---

# M47 CorrectionCase 整改 Task 自动创建

## 1. 实现范围

1. `REJECTED` 打开 CorrectionCase 时，同事务创建 HUMAN 整改 Task（`taskType=evidence.correction`，`businessKey=correctionCaseId`）；
2. CorrectionCase 持久化 `correctionTaskId`；责任人/SLA 仍只属于 Task；
3. Task 链接成功后 Case 状态进入 `IN_PROGRESS`（表示整改队列已激活，不等于审核通过）；
4. 补传/关闭语义保持 M45；`resubmit` 允许 `OPEN|IN_PROGRESS|RESUBMITTED`；
5. OpenAPI **0.22.0**；Flyway **V047**；staging **047/49**。

## 2. 未实现

候选人自动指派、车企回执、条件槽位、`WAIVED`；强制通过/重开见 M48。
