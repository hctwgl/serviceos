---
title: M18 TaskCompleted 与工作流线性推进事务切片
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
  - decisions/ADR-017-workflow-runtime-selection.md
---

# M18 TaskCompleted 与工作流线性推进事务切片

## 1. 目标与边界

M18 在 M17 可靠启动之后实现第一个可重复推进闭环：

```text
自动 Task 被 Worker 认领
  → 执行器返回成功
  → Task / ExecutionAttempt 落终态
  → task.execution.succeeded（技术执行事实）
  → task.completed@v1（业务完成事实）
  → Workflow 消费者 Inbox 去重
  → 按 versionId + contentDigest 读取冻结 WORKFLOW 资产
  → 锁定当前 NodeInstance 并核对完整上下文
  → 当前 NodeInstance COMPLETED
  → 创建唯一下一 NodeInstance 与 Task
  → Inbox complete
```

M18 的可执行子集限定为“任务节点 → 唯一无条件同阶段任务节点”。条件网关、并行、
跨阶段、END、等待事件、子流程以及人工任务动作不属于本切片；遇到这些定义必须失败关闭，
不得伪造流程完成或阶段迁移。

## 2. 为什么需要 TaskCompleted

`task.execution.succeeded` 表示某次自动执行尝试成功，它属于 Task/Scheduler 技术事实；
流程推进依赖的是“任务已满足业务完成条件”。两者必须分离：

- 普通后台 Task 成功只产生技术事实，不会误触发工作流；
- 带完整 Workflow/Stage/Node 上下文的 Task 成功，额外产生 `task.completed@v1`；
- 未来人工任务、审批任务也应在各自完成命令成功后产生相同业务事实；
- Workflow 只消费 `task.completed`，不感知 Worker、执行尝试或外部调用细节。

## 3. 冻结身份与失败关闭

消费者必须同时校验：

1. event envelope 的 module、aggregateType 与 aggregateId；
2. TaskCompleted 中的 Task、Project、WorkOrder、Workflow、Stage、NodeInstance、Node ID；
3. Task、Workflow 与事件中的 definition version ID 和 digest 完全一致；
4. Task 为 `SUCCEEDED`，Workflow/Stage/NodeInstance 均为 `ACTIVE`；
5. result digest 与 resultRef 一致；
6. 精确版本资产仍为 `PUBLISHED` 且摘要一致；
7. 当前节点只有一条无条件出边，目标为受支持的同阶段任务节点。

任何一项不满足，Inbox、节点更新和下一 Task 创建全部回滚。源 Outbox 按既有失败、退避和
人工接管策略处理，不允许回退读取“当前最新流程”。

## 4. 一致性与幂等

- Task 终态、ExecutionAttempt、两个 Task 事件位于同一 Task 结果事务；
- Inbox begin、当前节点完成、下一 Task/TaskCreated、下一节点创建和 Inbox complete 位于同一事务；
- 同一 `eventId + payloadDigest` 重放由 Inbox 返回 REPLAY，不重复生成节点或任务；
- 同一 `eventId` 不同摘要由 Inbox 拒绝；
- `wfl_node_instance` 对 Task、completion event 建立 tenant 内唯一约束；
- 当前节点使用行锁及 `status='ACTIVE'` 条件更新，阻止两个不同完成事件并发推进。

## 5. 数据迁移

V018 新增 `wfl_node_instance`，并为 Stage 增加 tenant 复合唯一键和节点外键。

M17 不具备正式 TaskCompleted 事件，因此无法从一个已经终态的旧 Task 可靠推导
`completion_event_id`。V018 在发现终态 Workflow Task 时主动失败，要求发布前完成显式修复；
对 PENDING、READY、CLAIMED、RETRY_WAIT Task 才允许安全回填 ACTIVE NodeInstance。

当前最高 versioned migration 为 `018`，加两个 repeatable 后成功记录数为 `20`；staging
发布门禁必须校验 `018/20`。

## 6. 自动化证据与限制

- `WorkflowDefinitionParserTest`：验证唯一线性下一任务与条件出边失败关闭；
- `LocalOutboxPublisherTest`：验证 `task.completed@v1` 在本地模式必须有消费者；
- `task-completed-v1.schema.json`：发布稳定事件契约及有效样本；
- `WorkflowBootstrapPostgresIT`：验证首节点实例与启动事务一致；
- `WorkflowLinearProgressionPostgresIT`：验证 Task 执行、事件、Inbox、节点完成、下一 Task、重放和跨阶段失败回滚。

当环境无 Docker 时，PostgreSQL IT 会被 Testcontainers 明确跳过。编译成功与单元测试通过不能
替代 V018 迁移及 PostgreSQL 原子链路的真实执行证据。
