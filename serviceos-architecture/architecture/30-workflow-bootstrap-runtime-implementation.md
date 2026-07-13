---
title: M17 工作流可靠启动事务切片
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
reviewers:
  - Product Architecture
  - Engineering Architecture
  - BYD Business Owner
approved_by: []
approved_at:
related_adrs:
  - decisions/ADR-002-versioned-configuration-bundle.md
  - decisions/ADR-014-local-transaction-outbox-inbox.md
  - decisions/ADR-017-workflow-runtime-semantics.md
---

# M17 工作流可靠启动事务切片

## 1. 目标与边界

M17 把 M16 的 `RECEIVED` 工单推进到可执行内核，但只负责一次可靠启动：

```text
ReceiveExternalWorkOrder 本地事务
  ├─ 写入 WorkOrder(RECEIVED)
  └─ 写入 workorder.received@v1 Outbox

Outbox Worker
  → Local/Broker Publisher
  → Workflow 消费者 Inbox 去重
  → 按 Bundle ID + Manifest Digest 读取精确 WORKFLOW 资产
  → 受控解释 START 后唯一首任务
  → 创建 Workflow(ACTIVE)
  → 创建 Stage(ACTIVE)
  → 创建首个 Task(PENDING/READY)
  → 激活 WorkOrder(ACTIVE)
  → 发出 workflow.started / stage.activated / task.created / workorder.activated
```

M17 不实现首任务完成后的节点推进、网关、并行、等待事件、子流程、负责人策略或 SLA 绑定。

## 2. 一致性与幂等

- 工单与 `workorder.received` 必须在同一数据库事务提交；
- 消费者 `Inbox begin`、Workflow/Stage/Task 写入、WorkOrder 激活、后续 Outbox 和 `Inbox complete` 位于同一事务；
- 同一 `eventId + payloadDigest` 重放直接返回，不产生重复实例；
- 同一 `eventId` 不同摘要由 Inbox 拒绝；
- 配置读取同时校验 tenant、bundleId、published 状态和 manifest digest，禁止回退到“最新流程”；
- 流程 JSON 不满足 M17 可执行子集时整笔消费者事务回滚，源 Outbox 标记失败并按既有退避策略重试。

## 3. 数据所有权

| 模块 | 权威事实 |
|---|---|
| workorder | WorkOrder 当前状态、配置包冻结引用、工单生命周期事件 |
| workflow | WorkflowInstance、StageInstance、流程/阶段事件 |
| task | Task、执行尝试、`task.created` |
| configuration | Published Asset/Bundle 及不可变内容摘要 |
| reliability | Outbox、Inbox、发布尝试、租约与退避 |

模块间只通过公开 API 或可靠事件协作；Workflow 不直接写 `wo_*` 或 `tsk_*` 表。

## 4. M17 工作流定义子集

首个可执行版本要求：

1. `semanticVersion` 与冻结配置资产版本一致；
2. `startNodeId` 指向唯一 `START` 节点；
3. `START` 恰有一条无条件出边；
4. 目标为 `USER_TASK`、`SERVICE_TASK`、`REVIEW_TASK` 或 `MANUAL_INTERVENTION`；
5. 首任务必须给出 `stageCode` 与 `taskType`；
6. `SERVICE_TASK` 创建 `AUTOMATED/PENDING` Task，其他三类创建 `HUMAN/READY` Task。

未声明的语义全部失败关闭，不能由运行时猜测。

## 5. 迁移与发布

- V015：冻结 Bundle manifest digest、增加 WorkOrder 激活时刻与生命周期约束；已有工单必须先执行显式事件补录计划；
- V016：给 Task 增加完整 Workflow/Stage/Node/Definition 上下文；
- V017：创建 `wfl_workflow_instance` 与 `wfl_stage_instance`；
- 当前最高 versioned migration 为 `017`，加两个 repeatable 后成功记录数为 `19`；
- staging Gate 必须同时核对 `017/19`，且必须包含 workflow migration location。

## 6. 自动化证据与限制

- `WorkflowDefinitionParserTest`：首节点解析、起点歧义和版本漂移失败关闭；
- `LocalOutboxPublisherTest`：本地路由、多消费者与无消费者失败关闭；
- 事件 Schema：五个新增事件均有不可变 v1 Schema 与有效样本；
- `WorkflowBootstrapPostgresIT`：验证事务链、精确版本、重放不重复和非法定义回滚。

当环境无 Docker 时 PostgreSQL IT 会被 Testcontainers 明确跳过；不得把“测试已编译”描述为“数据库链路已通过”。
