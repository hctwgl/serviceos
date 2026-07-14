---
title: M27 ServiceAssignment 激活超时对账与人工接管
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M27 ServiceAssignment 激活超时对账与人工接管

## 1. 目标

M27 落实服务网络设计中“任一步失败按幂等事件重试；超时产生 OperationalException，Task 保持不可执行”
的要求。M26 已能可靠向前推进或在切换前受控终止，但没有权威截止时间与重启补偿扫描，长期停留的 saga
只能依靠人工查库发现。

每次非终态推进现在都会冻结新的 `deadlineAt`：

```text
PENDING / TASK_PREPARED / SERVICE_SWITCHED / ABORTING
  → deadline 到期
  → stage + sagaVersion 唯一 TimeoutOccurrence
  → ServiceAssignmentActivationTimedOut@v1
  → OperationalException(DISPATCH/P1) + HUMAN handling Task
```

## 2. 不变量与事务边界

- deadline 由 Dispatch 权威事务写入，默认每阶段 15 分钟，可由部署配置覆盖；
- `COMPLETED/ABORTED` 终态必须清除 deadline，不能再次被扫描；
- 扫描使用 `FOR UPDATE ... SKIP LOCKED`，同一阶段/版本由数据库唯一约束保证只产生一次超时；
- TimeoutOccurrence、saga `lastErrorCode` 与 Outbox 在同一事务提交，Outbox 写失败时全部回滚；
- 检测超时不修改 saga stage/version，不与仍在途的前向消息争夺业务版本；
- 超时不结束新旧责任、不释放容量、不解除 guard，也不伪造 TaskAssignment 对齐；
- 同一 saga 后续阶段再次超时会保存新的 Dispatch occurrence，但异常中心聚合 occurrenceCount 并复用一个
  handling Task；
- Local Outbox 模式将 timeout 事件列为强制消费事件，缺少异常中心处理器时失败关闭。

## 3. 运行配置

```text
serviceos.dispatch.activation-stage-timeout=PT15M
serviceos.dispatch.activation-timeout-scheduling-enabled=true
serviceos.dispatch.activation-timeout-poll-delay=PT30S
serviceos.dispatch.activation-timeout-batch-size=100
```

定时扫描默认关闭，部署环境必须在确认 Outbox worker 与异常处理 Task 消费能力就绪后显式开启。手工或测试
调用同一 `ServiceAssignmentTimeoutScanner`，不维护第二套扫描语义。

## 4. 数据与契约

V027 新增 saga `deadline_at`、到期索引和不可变
`dsp_service_assignment_saga_timeout` occurrence 表；异常中心增加 workOrder/task 上下文、
`occurrence_count` 与 `last_detected_at`。当前 Flyway 为 `027/29`。

新增 `service.assignment.activation-timed-out@v1` Schema 与有效样本。事件以 saga 为 aggregate，精确携带
发生超时的 stage/version、deadline 和 detectedAt，不把扫描时间伪装成业务截止时间。

## 5. 尚未证明

M27 不替业务方决定超时后应继续重试、切换前 abort 还是切换后授权补偿；当前统一进入异常中心并保持
guard。初派握手、Network 双级派单、候选硬过滤与评分、切换后补偿命令、异常自动恢复关单、SLA 和完整
勘安链路仍需后续里程碑闭环。
