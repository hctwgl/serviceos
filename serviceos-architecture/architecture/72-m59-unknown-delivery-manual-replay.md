---
title: M59 UNKNOWN 外部交付人工重发运行时
version: 1.0.0
status: Implemented
---

# M59 UNKNOWN 外部交付人工重发运行时

## 1. 决策基线

M59 延续 M58、ARCH-13、ADR-010/014 和 API-04。项目负责人于 2026-07-15 批准继续按
指导文档实施；本切片只接受以下最小语义：

- 人工重发复用同一 `OutboundDelivery`、冻结 payload 和 `externalIdempotencyKey`；
- 原 UNKNOWN `DeliveryAttempt` 永久保留，不把“不知道”改写为失败或成功；
- 每次重发创建不可变 `DeliveryReplayRequest` 和新的自动 Task；Task 仍是唯一执行/重试时钟；
- 重发必须由 USER principal 携带原因、审批引用、预期 Delivery 聚合版本，并通过
  `integration.retryUnknownDelivery` HIGH capability 与实时 project scope；
- 本切片不猜测“人工标记已送达”或“放弃交付”的业务后果。

## 2. 实现范围

1. `POST /outbound-deliveries/{deliveryId}:retry` 对 UNKNOWN Delivery 发起幂等重发；
2. V059 建立 `int_delivery_replay_request`，以不可变授权字段、生命周期检查、单活跃请求
   唯一索引和 trigger 保护重放事实；
3. 命令事务内原子登记 Idempotency、ReplayRequest、Task、Outbox 与 Audit，不执行网络调用；
4. 新 Task 复用冻结 payload 摘要和原外部幂等键；网络前只在 REQUESTED ReplayRequest
   存在时允许 UNKNOWN → SENDING；
5. 每次真实网络动作新增连续 `DeliveryAttempt.attemptNo`，旧 UNKNOWN attempt 不修改；
6. 重放结果同步收敛 ReplayRequest；再次 UNKNOWN 仍转人工且绝不自动重发；
7. 外部已明确送达但本地 CLIENT Case/Route 落账失败时，只重试本地落账，不再次发送 HTTP；
8. Core OpenAPI 0.32.0 暴露命令与安全查询摘要，事件
   `integration.outbound-delivery-replay-requested@v1` 固定重放授权事实。

## 3. 事务、并发与失败语义

| 场景 | Delivery | ReplayRequest | Task / 网络 |
|---|---|---|---|
| 缺 capability、非 USER、缺原因/审批或版本过期 | 保持 UNKNOWN | 不创建 | 不创建 Task，不发网络 |
| 合法命令 | 保持 UNKNOWN | REQUESTED | 创建独立 Task |
| 网络开始 | UNKNOWN → SENDING | EXECUTING | 新增 Attempt |
| 再次结果不确定 | UNKNOWN | UNKNOWN | 人工接管，不自动重发 |
| 明确拒绝/发送前最终失败 | REJECTED / FAILED_FINAL | 同步终态 | 最终失败 |
| 明确成功 | DELIVERED → ACKNOWLEDGED | DELIVERED | CLIENT Case/Route 落账；本地失败只重试本地 |

命令使用 `expectedAggregateVersion` 防止操作者基于旧 UNKNOWN 摘要重放；数据库单活跃请求索引
防止不同幂等键并发创建两个重放 Task。ReplayRequest 授权字段与终态不可修改。

## 4. 明确非目标

- 不实现“人工标记已送达”“放弃交付”或远端状态查询；
- M59 本身不自动关闭 M58 创建的 OperationalException/HUMAN Task；该后续能力已由 M60 在严格 ACK
  恢复事实驱动下实现；
- 不实现批量 ReplayRequest 审批、二级审批/MFA 或通用 Connector；
- 不实现其他 CPIM 消息、生产凭据/对象存储、真实 sandbox、Portal 或通知中心；
- 不重新实现 Evidence 主线，不改变 ReviewCase 状态机。

## 5. 工程证据

验收范围见 [M59 验收矩阵](../testing/56-m59-unknown-delivery-manual-replay-acceptance.md)。
主要入口：

- `DefaultOutboundDeliveryService#retryUnknown`、`BydReviewSubmissionTaskHandler`；
- `JooqOutboundDeliveryRepository`、V059；
- Core OpenAPI 0.32.0 与 replay-requested 事件 Schema；
- `ReviewCasePostgresIT`、MVC Security、契约/客户端、Flyway 与 ArchitectureTest。

M59 只声明本文第 2 节，不能外推为 UNKNOWN 全部处置或完整集成平台完成。
