---
title: M61 Task 自然时长 SLA 时钟
version: 1.0.0
status: Implemented
---

# M61 Task 自然时长 SLA 时钟

## 1. 决策基线

M61 实现 ARCH-12 中不依赖未确认业务日历、暂停原因、预警阈值或收件人策略的最小可靠切片：

- Workflow Task 节点以 `slaRef` 显式选择 SLA；没有引用即明确不启用，不推断项目默认 SLA；
- SLA v1 仅接受 `TASK + TASK_CREATED/TASK_COMPLETED + ELAPSED`，并要求显式
  `targetDurationSeconds`；
- Task 创建时锁定 Bundle、SLA Version、摘要、开始事件和截止时间；完成事件按业务发生时间停止；
- 到期对账只形成 SLA 事实，不直接修改 Task 或 WorkOrder；
- `BREACHED` 是不可擦除历史，逾期完成形成 `MET_LATE`。

## 2. 实现范围

1. 配置发布门禁增加 `sla-v1.schema.json`；未知 Schema、缺失时长、身份错配一律拒绝；
2. Bundle 发布时，Workflow `slaRef` 必须精确命中同一 Bundle 内唯一 SLA，且该策略必须显式覆盖
   节点 `taskType`；
3. 首任务与线性后续任务均把 `slaRef` 冻结到 Task；幂等创建遇到不同 `slaRef` 失败关闭；
4. `task.created@v1` 通过 Inbox 创建 `SlaInstance + RUNNING ClockSegment + TARGET_DUE Milestone`，
   同事务追加 `sla.started@v1`；
5. `task.completed@v1/v2` 锁定实例和里程碑：截止时间内形成 `MET`，逾期先形成 `BREACHED` 再形成
   `MET_LATE`，领域事件 aggregateVersion 严格递增；
6. 对账使用数据库权威事实和 `FOR UPDATE ... SKIP LOCKED`；同时锁定 Task，Task 已完成但完成事件尚未
   消费时不误报超时；
7. V061 以 FK、CHECK 和 trigger 保护 Task/Policy 租户与冻结身份、合法状态迁移、版本递增、单一运行
   segment、单一 TARGET_DUE milestone 和终态不可变；
8. Inbox、实例、segment、milestone 与 Outbox 在同一消费者事务提交，任一步失败整体回滚。

## 3. 权威状态

```text
TaskCreated -> RUNNING(v1)
RUNNING -> MET(v2)                         completion <= deadline
RUNNING -> BREACHED(v2) -> MET_LATE(v3)   completion > deadline
RUNNING -> BREACHED(v2)                   reconciliation detects due
BREACHED -> MET_LATE(v3)                  later completion event
```

Milestone 的 `triggeredAt` 固定为计划截止时间，`detectedAt` 保存实际对账/迟到完成发现时间。这样调度延迟
不会改写业务 breach 时间。相同 eventId/digest 重放返回首次结果；相同 eventId 不同 digest 失败关闭。

## 4. 明确未实现

- BUSINESS 工作日历、节假日和跨日班次；
- 暂停/恢复、免责、重算和取消；
- 50%/80% 等预警、升级、通知、OperationalException 联动和收件人解析；
- WorkOrder/Dispatch/Review 等其他 subject/start/stop 组合；
- SLA HTTP 查询/命令、Portal 投影、考核/结算读取和历史迁移；
- 未经试点确认的默认时长、风险权重或项目兜底策略。

## 5. 工程证据

验收范围见 [M61 验收矩阵](../testing/58-m61-task-elapsed-sla-clock-acceptance.md)。主要入口：

- `ConfigurationAssetSchemaValidator`、`WorkflowDefinitionParser`、`DefaultTaskSchedulingService`；
- `JooqSlaClockService`、`TaskSlaEventHandler`、`SlaClockService`；
- V061、SLA 配置 Schema、`sla.started/breached/met@v1` 事件 Schema；
- `ConfigurationPublicationPostgresIT`、`SlaClockPostgresIT`、契约治理和 ArchitectureTest。

M61 只声明 Task 自然时长的开始、到期和停止事实，不代表 ARCH-12 的完整 SLA/通知/升级平台已完成。
