---
title: M22 TaskExecutionGuard 可靠运行时
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M22 TaskExecutionGuard 可靠运行时

## 1. 目标与边界

M22 在 Task 模块内实现改派切换窗口的失败关闭保护。后续 ServiceAssignment saga 使用稳定 saga ID
获取 `REASSIGNMENT` guard；guard ACTIVE 期间，人工 Task 的 claim、start、complete、release 以及候选快照
替换全部拒绝执行。只有外部责任事实已安全对齐或完成补偿后，调用方才能解除指定 guard。

本里程碑提供 Java 应用服务边界，不发布面向 Portal 的 HTTP 命令。未来 dispatch 事件适配器必须调用该边界，
不得跨模块写 `tsk_task_execution_guard` 或 `tsk_task_assignment`。

## 2. 不变量

- guard 只可建立在 READY、CLAIMED 或 RUNNING 的 HUMAN Task；终态 Task 不可重新保护；
- 同一 Task 同时最多一个 ACTIVE guard，同一租户的 guardType + guardKey 永不复用；
- 获取和解除都检查 Task 版本，并使版本递增；重复幂等请求返回首次冻结版本与时间；
- 解除必须精确匹配 taskId、guardId 与 ACTIVE 状态，不能用“解除全部”绕过错误 saga；
- ACTIVE guard 的判断在人工命令冲突分类中优先于陈旧版本，调用方得到稳定
  `TASK_EXECUTION_GUARDED`，且不会写入 Task、Assignment 或领域事件；
- Task、guard、审计、幂等和 Outbox 位于同一事务，事件写入失败全部回滚。

## 3. 契约与迁移

V022 新增 `tsk_task_execution_guard`、唯一 ACTIVE 索引和 `task.guard.manage` capability。事件新增
`task.execution-guard.activated@v1` 与 `task.execution-guard.released@v1`。当前 Flyway 版本为 `022`，
包含 repeatable 共 `24` 条，staging Gate 同步验证 `022/24`。

## 4. 尚未证明

M22 不实现 PREPARED TaskAssignment、ServiceAssignment、候选评估、容量预占、激活/补偿 saga 或超时异常任务，
也不实现 SLA 与离线撤权。guard release 的业务资格仍由未来 dispatch saga 保证；Task 模块只保证精确 guard、
并发排他、命令失败关闭和可靠事实。
