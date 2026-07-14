---
title: M19 跨阶段、END 与工单履约完成事务切片
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-002-versioned-configuration-bundle.md
  - decisions/ADR-014-local-transaction-outbox-inbox.md
  - decisions/ADR-017-workflow-runtime-selection.md
---

# M19 跨阶段、END 与工单履约完成事务切片

## 1. 目标与边界

M19 在 M18 的 TaskCompleted 消费事务中增加两条确定性路径：

1. 唯一无条件任务边跨越 `stageCode` 时，完成当前 Stage，创建下一 Stage、NodeInstance 和 Task；
2. 唯一无条件边到达 END 时，原子完成当前 Node、Stage、Workflow 和 WorkOrder。

条件边、多出边、网关、并行、等待事件和人工任务动作继续失败关闭。

## 2. 一致性

- 当前 Node 行锁、Inbox、Stage/Workflow/WorkOrder 状态和派生 Outbox 位于同一事务；
- 跨阶段先完成旧 Stage，再创建新 Stage 与 Task，任一写入失败整体回滚；
- END 产生 `stage.completed@v1`、`workflow.completed@v1` 和 `workorder.fulfilled@v1`；
- WorkOrder 只有从 ACTIVE 才能进入 FULFILLED，重复相同完成命令返回稳定重放结果；
- 事件携带冻结流程版本摘要，不能回退读取最新配置。

## 3. 数据与发布门禁

V019 为工单增加 `fulfilled_at` 及状态一致性约束。当前最高迁移版本为 `019`，包含两个
repeatable 后总记录数为 `21`；staging 必须校验 `019/21`。

## 4. 尚未证明

M19 不证明网关、并行、等待事件、人工任务动作、负责人/SLA、取消/重开、完整勘安业务流程
或正式 Broker。完整现场履约平台仍需后续里程碑与真实项目业务资产。
