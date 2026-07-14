---
title: M28 ServiceAssignment 超时异常自动恢复闭环
version: 0.1.0
status: Proposed
---

# M28 ServiceAssignment 超时异常自动恢复闭环

## 1. 目标与边界

M28 落地 M4 `EXC-004`：激活 saga 曾超时并进入人工接管后，如果可靠推进最终完成，系统必须以
真实 `ServiceAssignmentActivationCompleted` 事实自动解决异常，并合法终止仍未完成的人工 Task。

本切片不把 `ServiceAssignmentActivated` 当作恢复完成；该时点 TaskAssignment 尚可能未激活且 guard
仍存在。也不实现超时后的自动 abort/继续/补偿策略，不改变 M27 的失败关闭选择。

## 2. 可靠恢复链

```text
service.assignment.activation-completed@v1
  → Operations Inbox
  → 锁定 SERVICE_ASSIGNMENT_ACTIVATION_SAGA 异常
  → Task.cancelHandlingTask（精确 tenant/task/type/businessKey）
  → OperationalException RESOLVED + 领域恢复引用
  → task.cancelled@v1（仅实际取消时）
  → operational.exception.resolved@v1
  → Inbox SUCCEEDED
```

超时与完成事件使用同一 Task partition key。Local Outbox 与后续 Broker 路由必须保持该分区内顺序；
消费者仍以 Inbox 的 `eventId + schemaVersion + payloadDigest` 防止重放变造。

## 3. 不变量

- 只有 `activation-completed@v1` 且状态为 `ACTIVE`、原因是
  `TASK_ASSIGNMENT_ACTIVATED` 才可作为自动恢复证据；
- 异常、Task 取消、两个 Outbox 事件和 Inbox 完成位于同一数据库事务；任一步失败全部回滚；
- Task 模块拥有取消状态转换，Operations 不跨边界更新 `tsk_task`；
- 取消同时校验 Task UUID、类型和业务键，并撤销可能存在的活动 TaskAssignment；
- 已经 `COMPLETED` 的人工 Task 不被自动改写；异常仍可引用领域恢复事实转为 `RESOLVED`；
- 没有超时异常的正常 saga 完成只冻结 Inbox，不创建虚假异常、Task 或解决事件；
- `CANCELLED` Task 必须保存时间、原因和来源事件；`RESOLVED` 异常必须保存解决码、领域动作引用、
  来源事件和解决时间；
- 同一恢复事件重放不重复取消、不重复解决、不重复发事件，摘要变化失败关闭。

## 4. 数据与契约

V028 为 Task 增加 `cancelled_at`、`cancellation_reason_code`、
`cancellation_source_event_id`，为 OperationalException 增加 `resolution_code`、
`resolution_action_ref`、`resolution_event_id`，并用 CHECK/唯一索引约束终态证据。

新增不可变事件契约：

- `task.cancelled@v1`；
- `operational.exception.resolved@v1`。

当前 Flyway 基线为 `028/30`。

## 5. 验证证据

- `ServiceAssignmentRecoveryHandlerTest` 验证终态消息映射与身份失败关闭；
- `TaskExecutionPostgresIT` 验证自动解决、任务取消、重放、摘要变造、人工已完成保留、无超时正常路径，
  以及取消失败时异常/Inbox/Outbox 原子回滚；
- `ArchitectureTest` 验证 Operations 仅通过 Task API 协作；
- Contract tests 验证新增事件 schema 与样例。

## 6. 仍未闭环

M28 只关闭“超时后最终成功”的假告警。超时后的策略化 abort/继续/补偿、切换后授权补偿、初派握手、
Network 双级派单、候选硬过滤与可解释评分、SLA，以及完整勘安现场履约链路仍未实现。初派 READY/候选/
领取语义仍需业务确认，不能套用只接受 CLAIMED/RUNNING 的改派协议。
