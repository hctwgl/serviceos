---
title: M26 切换前可靠终止与持久检查点
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M26 切换前可靠终止与持久检查点

## 1. 目标

M26 落实服务网络设计中“`SERVICE_SWITCHED` 之前放弃”的补偿规则。M25 把
`TaskAssignmentPrepared` 的确认和 ServiceAssignment 激活放在同一消费者事务；激活失败会整体回滚到
`PENDING`，因此 Dispatch 不保存 Task 返回的 guard/PREPARED 引用，也无法可靠发起撤销。

M26 将其拆成两个本地事务，以 `service.assignment.task-prepared@v2` 作为持久检查点：

```text
TaskAssignmentPrepared@v1
  → Dispatch TASK_PREPARED:2（保存 guard/PREPARED 引用）
  → ServiceAssignmentTaskPrepared@v2
  → Dispatch SERVICE_SWITCHED:3
```

技术失败重试检查点事件即可继续向前；受控放弃则从 `TASK_PREPARED:2` 进入可靠终止链：

```text
Dispatch ABORTING:3 + 新责任 FAILED_ACTIVATION + HELD 容量释放
  → ServiceAssignmentActivationAborted@v2
  → Task PREPARED→ABORTED + guard RELEASED
  → TaskAssignmentAborted@v1
  → Dispatch ABORTED:4 + completedAt
```

## 2. 不变量与事务边界

- 正常激活只消费 `TASK_PREPARED:2`，authority/fence 仍使用 prepare 时冻结的证明；
- abort 的 Dispatch 本地事务先失败新责任并释放容量，但旧 ServiceAssignment 保持 ACTIVE；
- 只要 Task 尚未确认撤销，跨模块 saga 保持 `ABORTING`，不得伪装成终态；
- Task 撤销在同一事务中撤销 PREPARED、解除 guard、写审计/Outbox 并完成 Inbox；
- Task abort Outbox 写失败时，Task 领域写入与 Inbox begin 整体回滚，源消息可按同一 eventId 重试；
- 先入队的 task-prepared 检查点若已被 ABORTING/ABORTED 取代，只记录 SUCCEEDED Inbox，不得复活责任；
- v2 事件继续携带原发起人，每个消费者按当前 RoleGrant 实时复核授权；
- M24 v1 本地 abort 保持同步 `ABORTED` 语义，不误入 M26 跨模块处理器。

## 3. 数据与契约

V026 回填历史 ABORTED 的 `completed_at`，并要求 COMPLETED/ABORTED 两个终态必须具有完成时间。当前
Flyway 为 `026/28`。

新增 `service.assignment.task-prepared@v2`、`service.assignment.activation-aborted@v2` 和
`service.assignment.activation-abort-completed@v1` 契约；既有 v1 文件保持不可变。正常检查点、abort 请求和
Task abort 确认均属于本地适配器必须消费事件，缺少处理器时失败关闭。

## 4. 尚未证明

M26 尚未提供超时扫描器或自动选择 abort/向前重试的策略，也未实现初派 Task 握手、`SERVICE_SWITCHED`
之后的授权补偿、Network 双级派单、候选硬过滤与评分、SLA 和完整勘安链路。初派涉及 READY/候选/领取/
责任语义，必须先形成明确业务决策，不能直接复用只接受 CLAIMED/RUNNING 的改派命令。
