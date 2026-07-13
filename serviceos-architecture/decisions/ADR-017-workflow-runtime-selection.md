---
title: ADR-017 工作流运行时选择
version: 0.1.0
status: Proposed
owner: Architecture Board
reviewers:
  - Product Architecture
  - Engineering Architecture
approved_by: []
approved_at:
supersedes: []
related_adrs:
  - decisions/ADR-003-task-centric-execution.md
  - decisions/ADR-006-workflow-engine-as-orchestrator.md
---

# ADR-017：工作流运行时选择

## 上下文

ServiceOS 需要执行持续数小时到数月的现场履约流程，包含人工任务、系统任务、等待外部回执、超时、重试、人工接管、补偿和运行中版本锁定。平台又要求业务流程可配置，但不能把工单领域状态和关键不变量完全交给通用流程引擎。

当前需要在以下方案中做出选择：

1. 直接采用 Camunda/Flowable 等 BPMN 引擎作为业务核心；
2. 完全自研通用工作流引擎；
3. 采用 ServiceOS Task-centric 执行内核，自研受限流程定义与编排适配层；
4. 使用 Temporal 等 durable execution 平台承载全部业务执行。

## 决策

MVP 和首个试点采用方案 3：

> 以 ServiceOS 的 WorkOrder、Task、Review、Appointment、Dispatch 等领域对象作为事实源，流程运行时只负责编排，不拥有业务真相。

具体原则：

- 流程定义使用受限、版本化 JSON Schema；
- 每个用户任务、审核任务和系统任务必须映射为 ServiceOS Task 或独立领域聚合；
- 流程运行时不得直接修改业务表状态；
- 业务推进通过命令和领域事件完成；
- 长等待依赖持久化定时器、事件订阅和可靠消息；
- 自动任务失败进入统一重试和人工接管模型；
- 工单创建时锁定 Workflow Version 与 Configuration Bundle；
- 首期不建设完整 BPMN 设计器，不承诺 BPMN 全语义兼容；
- 运行时通过接口隔离，后续可替换为 Flowable、Camunda 或其他引擎。

## 运行时接口

工作流适配层至少暴露：

```text
startInstance(workOrderId, workflowVersionId, variables)
receiveDomainEvent(event)
completeNode(taskId, resultRef)
failNode(taskId, failure)
suspendInstance(workOrderId, reason)
resumeInstance(workOrderId, reason)
inspectInstance(workOrderId)
migrateInstance(workOrderId, targetWorkflowVersionId, migrationPlan)
```

任何接口调用必须具备幂等键、关联 ID、因果 ID 和审计上下文。

## 首期节点范围

MVP 仅支持：

- Start / End；
- User Task；
- Review Task；
- Service Task；
- Wait Event；
- Exclusive Gateway；
- Parallel Gateway；
- Manual Intervention；
- 受控 Sub Process。

暂不支持：

- 任意嵌套补偿语义；
- 动态修改已发布流程图；
- 任意脚本节点；
- 完整 BPMN 事件子流程；
- 业务人员直接操作引擎实例。

## 备选方案

### 通用 BPMN 引擎作为核心

优点：标准成熟、设计器丰富。缺点：容易把业务状态、权限和数据塞入流程变量，导致引擎成为事实源；复杂迁移和多租户治理成本高。暂不采用为核心，但保留适配可能。

### 完全自研通用引擎

优点：可控。缺点：成本高，容易重复制造 BPMN 引擎。拒绝建设通用引擎；只实现 ServiceOS 所需受限编排能力。

### Temporal 承载全部执行

优点：durable execution 和恢复能力强。缺点：人工任务、业务可视化、业务配置和 Java 团队运维模型需要额外建设。可用于未来高可靠自动化子流程，不作为首期业务编排核心。

## 后果

正面：

- 领域模型与流程引擎解耦；
- 可先完成可控 MVP；
- 运行历史、任务权限和业务审计统一；
- 保留替换或接入标准引擎的空间。

负面：

- 需要实现流程定义校验、实例投影、定时器和事件关联；
- 设计器能力首期有限；
- 团队必须严格禁止流程运行时直接写业务状态。

## 验证标准

ADR 转为 Accepted 前，必须用比亚迪勘安试点证明：

- 正常流程可执行；
- 勘测不通过分支可执行；
- 资料驳回可多轮整改；
- 外部回传失败可重试并转人工；
- SLA 暂停、恢复和升级不丢失；
- 应用重启后流程可恢复；
- 重复事件不会重复推进。

## 复评触发条件

- 需要 BPMN 标准交换；
- 流程节点类型超过当前模型可控范围；
- 自研运行时维护成本超过引入成熟引擎；
- 单实例节点数、并发量或定时器规模无法满足容量目标；
- 多团队需要独立流程设计和部署。
