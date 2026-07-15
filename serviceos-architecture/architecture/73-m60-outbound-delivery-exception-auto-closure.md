---
title: M60 外部交付恢复异常自动闭环
version: 1.0.0
status: Implemented
---

# M60 外部交付恢复异常自动闭环

## 1. 决策基线

M60 延续 M28、M58、M59、ARCH-13、ADR-010/014 和 API-04。它只使用 M59 授权重发后取得的
严格外部 ACK 作为成功事实，不新增人工猜测结果的命令：

- 重发 Task 收到严格 ACK 后，Integration 同事务追加
  `integration.outbound-delivery-recovered@v1`；
- Operations 以 Inbox 消费恢复事实，关闭该 Delivery 历次 UNKNOWN 执行 Task 对应的异常；
- 未完成的处理 Task 通过 Task 模块公开 API 精确取消；已完成的处理 Task 保留历史；
- 恢复事件先于延迟失败事件到达时，先登记不可变恢复标记；迟到失败只形成已解决历史，不再创建
  HUMAN Task；
- 同一 eventId 重复投递返回首次结果，不同 digest 失败关闭。

## 2. 实现范围

1. ACK 事件 causation 指向本次实际成功的执行 Task；仅当 Delivery 存在 ReplayRequest 时追加恢复事件；
2. 恢复 payload 冻结 Delivery、成功执行 Task、原始及全部重发执行 Task 与 ACK 时间；
3. Operations 按 tenant + sourceTaskId 获取事务级 advisory lock，串行化“失败开异常”和“恢复关异常”；
4. V060 建立不可变 `ops_task_failure_recovery`，由外键、类型检查、租户/Task 类型 trigger 和禁止
   UPDATE/DELETE trigger 保护；
5. 已存在 OPEN/ACKNOWLEDGED 异常时，同事务登记恢复、取消处理 Task、转 RESOLVED、完成 Inbox 并
   追加 `operational.exception.resolved@v2`；
6. 恢复先到时，同事务登记恢复标记；迟到失败事件创建 RESOLVED 异常历史并追加
   `handlingTaskStatus=NOT_CREATED` 的 v2 事件，不创建处理 Task；
7. 任一步失败均回滚恢复标记、异常、Task 变更、Inbox 和 Outbox，不产生部分闭环。

## 3. 事务与乱序语义

| 到达顺序 | OperationalException | HUMAN Task | 恢复标记 |
|---|---|---|---|
| 失败 → 恢复 | OPEN/ACKNOWLEDGED → RESOLVED | 未完成则 CANCELLED；已完成则保留 | INSERT |
| 恢复 → 失败 | 迟到失败直接形成 RESOLVED 历史 | 不创建 | 已存在 |
| 恢复重复投递 | 不重复迁移或发事件 | 不重复取消 | 复用首次 Inbox 结果 |

锁键只覆盖同租户同源 Task，不扩大为全局锁。恢复事件列出同一 Delivery 的全部执行 Task，因此可关闭
原始 UNKNOWN 与多轮 UNKNOWN 重发留下的异常；不扫描或关联其他 Delivery。

## 4. 明确非目标

- 不实现人工标记已送达、放弃交付或协议不存在的远端状态查询；
- 不把 HTTP 发送成功、超时或本地猜测当作 ACK；
- 不实现批量重放审批、通知通道、运营中心前端或通用 Connector；
- 不重新实现 Evidence 主线，不改变 ReviewCase 状态机。

## 5. 工程证据

验收范围见 [M60 验收矩阵](../testing/57-m60-outbound-delivery-exception-auto-closure-acceptance.md)。
主要入口：

- `OutboundDeliveryCompletionService`、`OutboundDeliveryRecoveryHandler`；
- `DefaultOperationalExceptionService#resolveTaskFailures`；
- V060、两个 v1/v2 事件 Schema；
- `ReviewCasePostgresIT`、`TaskExecutionPostgresIT`、Handler/Contract 与 ArchitectureTest。

M60 只声明“获权重发取得严格 ACK 后”的异常闭环，不能外推为 UNKNOWN 全部人工处置或完整运营平台。
