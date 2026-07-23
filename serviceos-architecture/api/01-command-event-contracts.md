---
title: 核心命令与事件契约
version: 0.1.0
status: Proposed
---

# 核心命令与事件契约

## 1. 契约原则

- 命令表达请求做什么，使用祈使语义；
- 事件表达已经发生什么，使用过去式；
- API 不接受客户端直接提交目标状态；
- 所有写命令支持幂等和乐观并发；
- 事件是跨模块通知契约，不暴露内部数据库结构；
- 契约采用向后兼容的版本策略，破坏性变更新主版本。

## 2. 通用命令信封

```json
{
  "commandId": "01J...",
  "commandType": "CompleteTask",
  "commandVersion": 1,
  "idempotencyKey": "client-generated-key",
  "aggregateId": "TASK-2026-0001",
  "expectedVersion": 12,
  "authorityVersion": 7,
  "actorContextRef": "SERVER-AUTH-CONTEXT",
  "correlationId": "TRACE-...",
  "occurredAt": "2026-07-12T10:00:00+08:00",
  "payload": {}
}
```

`actorContextRef` 由服务端网关/应用层注入，不由客户端提交。用户、组织、角色和授权决策从可信认证上下文解析。

`authorityVersion` 适用于已经分配权威系统的工单及其子域命令。外部 HTTP 客户端通过 `X-Work-Order-Authority-Version` 提交等价值；内部消息由可信 envelope 注入。应用服务在事务提交前与当前 WorkOrderAuthorityAssignment 复核，版本过期、SHADOW/READ_ONLY 或非权威系统请求不得写业务表和 Outbox。

## 3. 通用结果

```json
{
  "commandId": "01J...",
  "result": "ACCEPTED",
  "aggregateId": "TASK-2026-0001",
  "aggregateVersion": 13,
  "eventIds": ["EVT-..."],
  "links": {
    "resource": "/tasks/TASK-2026-0001"
  }
}
```

错误至少区分：验证失败、无权限、状态冲突、版本冲突、幂等键冲突、配置缺失和暂时性系统错误。

## 4. 首批命令目录

| 命令 | 聚合 | 关键前置条件 | 结果事件 |
|---|---|---|---|
| `CreateWorkOrder` | WorkOrder | 外部幂等通过（履约方案版本在受理时绑定） | `WorkOrderCreated` |
| `ActivateWorkOrder` | WorkOrder | 必需初始信息完整、匹配唯一履约方案并绑定其生效版本；无法唯一确定进入待确认 | `WorkOrderActivated`、`WorkOrderFulfillmentPlanMatched` |
| `ManualAssignFulfillmentPlan` | WorkOrder | 待确认/异常；方案属本项目、ENABLED、有生效版本、用户有权限、生命周期允许、不匹配填例外原因 | `WorkOrderFulfillmentPlanMatched` |
| `RematchFulfillmentPlan` | WorkOrder | 已受理未派网点；阶段允许重匹配 | `WorkOrderFulfillmentPlanRematched` |
| `AdjustFulfillmentPlan` | WorkOrder | 已派网点未开工；高风险授权；重校验责任链与 SLA | `WorkOrderFulfillmentPlanAdjusted` |
| `CreateTask` | Task | 定义版本存在、业务键不重复 | `TaskCreated` |
| `ClaimTask` | Task | READY、操作者为候选人 | `TaskClaimed` |
| `ReleaseTask` | Task | CLAIMED、操作者为当前领取人或管理员 | `TaskReleased` |
| `StartTask` | Task | CLAIMED/READY 且类型允许 | `TaskStarted` |
| `CompleteTask` | Task | RUNNING、完成条件满足 | `TaskCompleted` |
| `BlockTask` | Task | 可阻塞、原因有效 | `TaskBlocked` |
| `ResolveBlock` | Task | BLOCKED、解决信息完整 | `TaskBlockResolved` |
| `RetryTask` | Task | FAILED/人工接管、仍可重试 | `TaskRetryRequested` |
| `ManualComplete` | Task | MANUAL_INTERVENTION、审批和修复依据完整 | `TaskManuallyCompleted` |
| `CancelTask` | Task | 允许取消、补偿要求已满足 | `TaskCancelled` |
| `SuspendWorkOrder` | WorkOrder | ACTIVE、暂停原因有效 | `WorkOrderSuspended` |
| `ResumeWorkOrder` | WorkOrder | SUSPENDED、恢复条件满足 | `WorkOrderResumed` |
| `ConfirmFulfillment` | WorkOrder | 项目验收条件满足 | `WorkOrderFulfilled` |
| `CloseWorkOrder` | WorkOrder | FULFILLED、关闭条件满足 | `WorkOrderClosed` |
| `CancelWorkOrder` | WorkOrder | 允许取消、补偿计划有效 | `WorkOrderCancelled` |
| `ReopenWorkOrder` | WorkOrder | 高风险授权、恢复点有效 | `WorkOrderReopened` |
| `MigrateConfiguration` | WorkOrder | 迁移计划审批通过 | `WorkOrderConfigurationMigrated` |
| `CorrectWorkOrderData` | WorkOrder | 字段归属/阶段/FieldPolicy/影响满足 | `WorkOrderDataCorrected` |
| `ForceCloseWorkOrder` | WorkOrder | 专用授权、影响/补偿/审批满足 | `WorkOrderForceClosed` |

## 5. 通用事件信封

```json
{
  "eventId": "EVT-...",
  "eventType": "TaskCompleted",
  "eventVersion": 1,
  "aggregateType": "Task",
  "aggregateId": "TASK-...",
  "aggregateVersion": 7,
  "occurredAt": "2026-07-12T10:01:00+08:00",
  "actorId": "U-1001",
  "correlationId": "TRACE-...",
  "causationId": "01J...",
  "tenantId": "TENANT-1",
  "payload": {}
}
```

## 6. 事件目录

| 事件 | 主要消费者 | 最小载荷 |
|---|---|---|
| `WorkOrderCreated` | 流程适配器、审计、投影 | 工单、项目、业务产品（履约方案版本在受理时绑定，见 WorkOrderFulfillmentPlanMatched） |
| `WorkOrderFulfillmentPlanMatched` | 流程适配器、SLA、投影、审计 | 工单、fulfillmentPlanId、fulfillmentPlanVersionId、matchMode、匹配解释 |
| `TaskReady` | 待办投影、通知、SLA | 任务、类型、候选人、SLA ID |
| `TaskStarted` | 时间线、SLA | 任务、执行人、输入版本 |
| `TaskCompleted` | 流程适配器、时间线 | 任务、定义版本、完成结果引用 |
| `TaskBlocked` | 流程适配器、SLA、待办 | 原因、证据、是否申请暂停 SLA |
| `TaskBlockResolved` | 流程适配器、SLA | 解决结果、恢复时间 |
| `TaskFailed` | 任务模块重试调度器、运维投影 | 错误分类、尝试次数、下次重试时间 |
| `ManualInterventionRequired` | 待办、通知、运营 | 任务、接管角色、失败摘要、SLA |
| `EvidenceApproved` | 流程适配器、事实提取 | 资料、审核案例、对象版本 |
| `ClientReviewRejected` | 客服协调、流程适配器 | 外部回执、原因、受影响对象 |
| `FulfillmentFactsConfirmed` | 计价 | 工单、事实版本集合 |
| `CalculationCompleted` | 结算、投影 | 试算、方向、价格版本、金额摘要 |
| `WorkOrderSuspended` | 流程适配器、SLA、时间线 | 工单、原因、影响范围 |
| `WorkOrderResumed` | 流程适配器、SLA、时间线 | 工单、恢复原因和时间 |
| `WorkOrderClosed` | 投影、集成、审计 | 工单、关闭原因、最终版本 |
| `WorkOrderReopened` | 流程适配器、审计 | 工单、模式、恢复点、审批引用 |
| `WorkOrderDataCorrected` | 派单、SLA、事实、投影、审计 | fieldCode、old/new digest、correction/impact ref |
| `WorkOrderForceClosed` | 流程、任务、预约、派单、集成、审计 | 原状态、原因、补偿/影响 refs、审批 |

## 7. 兼容规则

- 可以新增可选字段，消费者必须忽略未知字段；
- 不删除或改变已有字段语义；
- 枚举新增值时消费者必须有 UNKNOWN/兜底策略；
- 破坏性变更发布新事件版本并支持双写迁移期；
- 事件 Schema 进入注册表并在 CI 中校验兼容性。

## 8. 隐私与最小载荷

事件默认只传标识和必要摘要，不广播手机号、详细地址、身份证、照片 URL 或完整表单。消费者需要敏感信息时通过授权 API 按需获取，并记录访问审计。
