---
title: 领域事件目录与发布规则
version: 0.1.0
status: Proposed
---

# 领域事件目录与发布规则

本文冻结 ServiceOS 的领域事件命名、所有权、载荷和发布边界。事件描述已经发生的事实，不替代命令，不承担同步查询，也不作为跨聚合共享可变状态。

## 1. 基本规则

- 事件名称使用过去式，例如 `WorkOrderReceived`、`TaskCompleted`；
- 事件由事实源聚合产生，其他上下文只能消费，不能伪造；
- 一个已发布事件版本语义不可原地修改；
- 事件载荷只包含稳定标识、必要快照、发生时间和追踪元数据；
- 禁止把数据库实体、完整聚合对象或车企原始 DTO 直接作为事件载荷；
- 事件必须通过 Outbox 与业务事务原子提交；
- 消费者必须通过 Inbox 或等价机制实现幂等；
- 默认不采用事件溯源，领域表仍是当前事实源，事件用于集成、投影、审计和异步副作用。

## 2. 通用事件信封

所有跨模块和跨进程事件至少包含：

```json
{
  "eventId": "uuid",
  "eventType": "work-order.received",
  "eventVersion": 1,
  "aggregateType": "WorkOrder",
  "aggregateId": "uuid",
  "tenantId": "tenant-code",
  "projectId": "project-id",
  "occurredAt": "2026-07-13T10:00:00Z",
  "correlationId": "correlation-id",
  "causationId": "command-or-event-id",
  "traceParent": "00-...",
  "payload": {}
}
```

### 2.1 标识语义

- `eventId`：一次事实发布的全局唯一 ID；
- `correlationId`：同一业务链路的关联标识；
- `causationId`：直接触发本事件的命令或前序事件 ID；
- `aggregateId`：产生事件的聚合根 ID；
- `eventVersion`：事件载荷 Schema 版本，不是聚合版本。

## 3. WorkOrder 事件

| 事件 | 事实源 | 触发条件 | 最小载荷 |
|---|---|---|---|
| `WorkOrderReceived` | WorkOrder | 外部订单首次被 ServiceOS 接受 | workOrderId、externalOrderCode、clientCode、brandCode、serviceProductCode、bundleRef |
| `WorkOrderReceptionReplayed` | WorkOrder Application | 相同外部订单与相同摘要安全重放 | workOrderId、externalOrderCode、originalReceivedAt |
| `WorkOrderActivated` | WorkOrder | 初始校验完成并进入履约 | workOrderId、activatedAt |
| `WorkOrderSuspended` | WorkOrder | 符合暂停策略并成功暂停 | workOrderId、reasonCode、pausedSlaIds |
| `WorkOrderResumed` | WorkOrder | 暂停原因解除并恢复 | workOrderId、resumeReason |
| `WorkOrderCancelled` | WorkOrder | 用户或车企取消生效 | workOrderId、source、reasonCode |
| `WorkOrderFulfilled` | WorkOrder | 必要履约任务全部完成 | workOrderId、completedStageCodes |
| `WorkOrderClosed` | WorkOrder | 满足关闭条件并最终关闭 | workOrderId、closeType、closedBy |
| `WorkOrderReopened` | WorkOrder | 获授权的新履约周期被开启 | workOrderId、previousCloseType、newLifecycleId |

`WorkOrderReceptionReplayed` 默认是应用观测事件，不推进流程，也不得再次创建任务。

## 4. Task 事件

| 事件 | 触发条件 |
|---|---|
| `TaskCreated` | 流程运行时生成一个新的任务实例 |
| `TaskReady` | 前置条件满足，可进入执行队列 |
| `TaskAssigned` | 责任人或候选人解析完成 |
| `TaskClaimed` | 候选人正式领取任务 |
| `TaskStarted` | 执行实际开始 |
| `TaskCompleted` | 完成条件已满足且结果已提交 |
| `TaskBlocked` | 依赖、外部条件或业务异常阻塞 |
| `TaskRetryScheduled` | 自动任务失败后安排下一次重试 |
| `TaskManualInterventionRequired` | 重试耗尽或不可自动恢复 |
| `TaskCancelled` | 所属流程分支取消或工单终止 |

任务事件只表达执行外壳事实。勘测结果、审核决定、派单结论等业务事实必须由对应聚合发布。

## 5. Evidence 与 Review 事件

### 5.1 Evidence

- `EvidenceSlotActivated`
- `EvidenceSubmitted`
- `EvidenceRevisionAdded`
- `EvidenceValidationCompleted`
- `EvidenceSetSnapshotted`
- `EvidenceLocked`

`EvidenceSubmitted` 必须指向明确 revision；补传产生新 revision，不覆盖旧版本。

### 5.2 Review

- `ReviewCaseOpened`
- `ReviewDecisionRecorded`
- `ReviewApproved`
- `ReviewRejected`
- `CorrectionRequested`
- `ReviewForceApproved`
- `ExternalReviewResultReceived`

`ReviewForceApproved` 必须携带授权依据、操作者和原因，禁止与普通通过共用事件语义。

## 6. Dispatch、SLA、Integration 与 Settlement 事件

### 6.1 Dispatch

- `DispatchRequested`
- `DispatchCandidatesEvaluated`
- `DispatchDecisionMade`
- `ServiceNetworkAssigned`
- `DispatchFailed`
- `ServiceAssignmentRevoked`

### 6.2 SLA

- `SlaStarted`
- `SlaPaused`
- `SlaResumed`
- `SlaWarningRaised`
- `SlaBreached`
- `SlaEscalated`
- `SlaStopped`

### 6.3 Integration

- `InboundMessageAccepted`
- `InboundMessageRejected`
- `OutboundDeliveryQueued`
- `OutboundDeliverySucceeded`
- `OutboundDeliveryPartiallySucceeded`
- `OutboundDeliveryFailed`
- `ExternalCallbackReceived`

### 6.4 Pricing 与 Settlement

- `FulfillmentFactRecorded`
- `FactSetSnapshotted`
- `CalculationRunCompleted`
- `ReceivableCalculated`
- `PayableCalculated`
- `SettlementStatementGenerated`
- `SettlementStatementConfirmed`
- `SettlementAdjustmentRecorded`

## 7. 事件版本策略

兼容性原则：

- 增加可选字段：通常保持当前版本；
- 删除字段、修改含义、修改类型、修改必填性：发布新版本；
- 新版本事件采用同一逻辑事件名和递增 `eventVersion`；
- 消费者必须明确支持的版本范围；
- 旧版本在保留期内继续发布或通过转换器兼容，禁止静默切换；
- 事件 Schema 必须进入契约兼容 CI。

## 8. 事务与失败处理

```text
Command
  -> Aggregate validates invariant
  -> Persist aggregate
  -> Persist outbox event
  -> Commit one database transaction
  -> Worker publishes
  -> Consumer inbox deduplicates
  -> Consumer applies side effect
```

禁止：

- 数据库提交后再临时构造事件且无 Outbox；
- 在聚合事务中同步调用车企接口；
- 消费失败时无限重试；
- 用消息队列投递成功代替业务成功；
- 通过事件修改另一个上下文的内部表。

## 9. 测试要求

每个正式事件至少需要：

1. 正向 Schema 样例；
2. 缺少必填字段的反向样例；
3. 幂等消费测试；
4. Outbox 与业务写入原子性测试；
5. 版本兼容门禁；
6. 敏感字段和日志脱敏测试；
7. 重试耗尽与人工接管测试（如适用）。
