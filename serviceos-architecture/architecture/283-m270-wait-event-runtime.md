---
title: M270 WAIT_EVENT 运行时
status: Implemented
milestone: M270
lastUpdated: 2026-07-18
relatedMilestones: [M19, M268, M269]
---

# M270 WAIT_EVENT 运行时

## 目标

支持流程在 `WAIT_EVENT` 节点按事件类型 + 关联键挂起，并在匹配信号到达后幂等唤醒、继续推进。

## 范围与非目标

- 范围：
  - Workflow schema：`waitEventType` / `correlationKeyTemplate`；
  - Flyway V101：`wfl_wait_subscription` + node 状态 `WAITING`；
  - 推进到 WAIT_EVENT 时登记订阅；
  - `WorkflowWaitSignalService.signal` 幂等唤醒；
  - 自动化测试。
- 明确不做：定时器超时自动唤醒、PARALLEL、子流程、Portal UI、全量外部事件自动 fan-in。

## 已实现

- 发布期校验 WAIT_EVENT 必填字段与唯一无条件出边；
- TaskCompleted 推进到 WAIT_EVENT 时创建 WAITING 节点与订阅（无 Task）；
- `signal` 按 `(tenant, waitEventType, correlationKey)` 唤醒；完成后重复信号安全重放；
- 关联键模板支持 `{workOrderId}` / `{projectId}` / `{workflowInstanceId}` / `{tenantId}`。

## 工程证据

- Flyway `V101__add_workflow_wait_event_runtime.sql`
- `WorkflowDefinitionParserTest` / `ConfigurationAssetSchemaValidatorTest`
- `WorkflowWaitEventPostgresIT` + Linear/Gateway 回归
