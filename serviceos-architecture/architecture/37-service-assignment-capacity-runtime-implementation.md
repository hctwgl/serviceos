---
title: M24 ServiceAssignment 与容量权威运行时
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
related_adrs:
  - decisions/ADR-014-local-transaction-outbox-inbox.md
---

# M24 ServiceAssignment 与容量权威运行时

## 1. 目标与边界

M24 首次落地 Dispatch 模块的 ServiceAssignment、CapacityCounter、CapacityReservation 与激活 saga。
Dispatch 只保存 Task 模块返回的 guard 与 PREPARED TaskAssignment 引用，不跨模块写 Task 表；后续由
Inbox 编排器把 M23 和 M24 两侧命令串成可靠握手。

## 2. 核心状态机

- `prepare`：校验旧 ACTIVE 责任，按容量版本占用一个单位，并原子创建
  `PENDING_ACTIVATION + HELD + PENDING`；
- `confirmTaskPrepared`：记录 guard/TaskAssignment，saga 进入 `TASK_PREPARED`；
- `activate`：原子结束旧 ACTIVE、释放旧 CONFIRMED 容量、激活新责任、确认新 HELD 容量，saga 进入
  `SERVICE_SWITCHED`，同时固化 authority 与 fence 证据；
- `abort`：只允许切换前执行，标记 `FAILED_ACTIVATION`、释放 HELD 容量且保留旧责任；
- `complete`：收到 TaskAssignmentActivated 证明后把 saga 从 `SERVICE_SWITCHED` 完成。

所有写命令都经过 capability 授权、乐观版本、冻结幂等、审计和 Outbox；事件写入失败会回滚本地
Assignment、Reservation、Counter、Saga 与命令结果。

## 3. 数据与契约

V024 新增容量计数器、服务责任、容量预留、激活 saga 与两类冻结命令结果表，并新增
`dispatch.capacity.configure`、`dispatch.assignment.manage` capability。当前 Flyway 基线为 `024/26`。

事件契约包括 `dispatch.capacity-configured@v1`，以及 `service.assignment` 的
pending-activation、task-prepared、activated、activation-aborted、activation-completed 五个事实。

## 4. 尚未证明

M24 尚未实现 Inbox 跨模块编排、候选筛选/派单策略、Network membership、`SERVICE_SWITCHED` 后补偿、
saga 超时与 OperationalException、SLA 和完整勘安业务链路；这些必须在后续里程碑继续闭环。
