# ADR-014：本地事务与 Outbox/Inbox 构成可靠消息基线

- 状态：Proposed
- 日期：2026-07-13

## 背景

工单创建、任务推进、派单、通知、车企回传和试算包含大量异步协作。如果数据库提交和消息发送分别执行，会出现状态已提交但事件丢失，或事件已发送但状态未保存。引入分布式事务会显著增加 MVP 复杂度，且无法覆盖所有外部 SaaS。

## 决策

每个领域命令只使用一个 PostgreSQL 本地事务；聚合状态、审计摘要、幂等结果和 OutboxRecord 同事务提交。Worker 至少一次发布，消费者用 InboxRecord + 领域唯一约束幂等处理。

消息 Broker 可作为吞吐、路由和解耦传输，但 PostgreSQL Outbox/Inbox 是可恢复事实源。业务自动重试由 Task 唯一调度；DeliveryAttempt/NotificationAttempt 只记录技术尝试。

## 约束

- 事务内禁止外部网络调用；
- eventId 稳定，重复发布不能生成新 eventId；
- 同 eventId 不同 digest 视为契约/安全异常；
- worker claim 使用租约并可崩溃恢复；
- 需要顺序时使用稳定 partitionKey，消费者仍校验 aggregateVersion；
- 外部 UNKNOWN 结果先查询/对账，不盲重发；
- 最终失败产生 OperationalException 和处理 Task；
- Outbox/Inbox/Task backlog age 必须可观测并有 runbook。

## 后果

系统接受“可能重复投递”，换取不依赖 XA 的可恢复性。所有消费者和外部副作用必须实现幂等与结果对账，同时数据库会承担 Outbox/Inbox 存储、清理和索引成本。

## 复审触发

- Outbox 表吞吐或保留成本经过优化仍无法满足签署 SLO；
- 独立服务拆分要求跨数据库事件传输；
- Broker 提供的事务能力能够覆盖业务数据库且运维成熟度足够；
- 法规要求改变事件保留与审计边界。
