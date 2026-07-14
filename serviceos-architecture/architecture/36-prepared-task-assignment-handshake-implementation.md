---
title: M23 PREPARED TaskAssignment 可靠激活握手
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M23 PREPARED TaskAssignment 可靠激活握手

## 1. 目标与边界

M23 完成改派协议的 Task 侧三段状态机。`prepare` 在一个本地事务中建立 ACTIVE
`REASSIGNMENT` guard 和 PREPARED RESPONSIBLE；`activate` 只接受同一 ServiceAssignment ID 的
激活确认，随后撤销旧候选/责任、激活新责任、创建新候选、切换 `claimed_by` 并解除 guard；
`abort` 仅用于外部 ServiceAssignment 尚未切换的路径，撤销 PREPARED 并保留旧责任。

该边界是 Java 应用服务，供后续 Dispatch Inbox 事件适配器调用。Dispatch 不得直接写 Task 表。

## 2. 不变量

- prepare 只接受 CLAIMED/RUNNING HUMAN Task，且必须存在不同于新主体的 ACTIVE RESPONSIBLE；
- guard 与 PREPARED assignment 同事务创建，任何一个失败都不能留下半状态；
- 每个 Task 最多一个 PREPARED RESPONSIBLE，preparationKey 在租户内不可复用；
- PREPARED 不具有 `effective_from`，因此不能通过 claim/start/complete 的 ACTIVE 责任门禁；
- activate 必须精确匹配 taskId、guardId、preparedAssignmentId 和 ServiceAssignment ID；
- activate 在同一事务关闭旧候选/责任、激活新责任与候选、切换 Task 主体并解除 guard；
- abort 不修改旧候选、旧责任或 `claimed_by`；外部责任已切换后不得调用普通 abort；
- prepare/activate/abort 均冻结首次幂等响应，并原子写审计与 Outbox。

## 3. 迁移与契约

V023 为 TaskAssignment 增加 PREPARED/ABORTED、guard/preparation/activation 引用和冻结命令结果，
并新增 `task.reassignment.manage` capability。事件新增 `task.assignment-prepared@v1`、
`task.assignment-activated@v1`、`task.assignment-aborted@v1`。当前 Flyway 为 `023/25`。

## 4. 尚未证明

M23 尚未实现 DispatchRequest/Decision、ServiceAssignment 聚合、容量 reservation、Inbox 事件编排、
saga 超时扫描、`SERVICE_SWITCHED` 之后的授权补偿或 OperationalException。当前测试以稳定
ServiceAssignment ID 模拟外部确认；完整跨模块闭环属于后续里程碑。
