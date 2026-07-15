---
title: M60 外部交付恢复异常自动闭环验收矩阵
version: 1.0.0
status: Implemented
---

# M60 外部交付恢复异常自动闭环验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M60-EVT-001 | P0 | M59 重发取得严格 ACK | 同事务追加 recovered@v1，成功 Task 与全部恢复 Task 精确冻结 |
| M60-EVT-002 | P0 | envelope/payload 身份错配、重复 Task 或成功 Task 不在集合 | 消费失败关闭，无 Operations 副作用 |
| M60-REC-001 | P0 | UNKNOWN 失败异常已打开后收到恢复 | 异常转 RESOLVED，未完成 HUMAN Task 取消，追加 resolved@v2 |
| M60-REC-002 | P0 | 处理 Task 已完成后收到恢复 | 保留已完成历史，仅解决异常，不改写 Task 结果 |
| M60-ORD-001 | P0 | 恢复事件先于延迟失败事件 | 先登记恢复标记；迟到失败只建 RESOLVED 历史，不建 HUMAN Task |
| M60-IDM-001 | P0 | 相同恢复 eventId/digest 重复投递 | 返回首次结果，不重复迁移、取消或追加 Outbox |
| M60-IDM-002 | P0 | 相同 eventId 不同 digest | 失败关闭，不接受被改写事件 |
| M60-TX-001 | P0 | 取消 Task 或异常写入失败 | marker/exception/Task/Inbox/Outbox 整体回滚 |
| M60-DB-001 | P0 | marker 指向跨租户、错误类型 Task 或尝试修改/删除 | PostgreSQL FK/CHECK/trigger 拒绝 |
| M60-CON-001 | P0 | 两个新事件 Schema 与样例 | Schema 可解析、有效样例通过、事件版本治理通过 |
| M60-MOD-001 | P0 | Integration → Operations → Task 协作 | 仅使用公开 API，ArchitectureTest 通过 |
| M60-MIG-001 | P0 | 空库迁移 | PostgreSQL 18 原生镜像应用 62 个迁移并到达 v060 |

## 自动化证据映射

- `ReviewCasePostgresIT#authorizedManualReplayPreservesUnknownAttemptAndFrozenPayload`：
  M60-EVT-001、REC-001、IDM-001；
- `TaskExecutionPostgresIT#recoveryBeforeFailureEventCreatesResolvedHistoryWithoutHandlingTask`：
  M60-ORD-001、IDM-001、DB-001、CON-001、MIG-001；
- `OutboundDeliveryRecoveryHandlerTest`：M60-EVT-002；
- M28 的异常恢复 PostgreSQL 回归覆盖已完成处理 Task 保留和 Task 取消失败整体回滚：
  M60-REC-002、TX-001；
- `ContractValidationTest`、`EventSchemaGovernanceTest` 与 `ArchitectureTest`：M60-CON/MOD。

本矩阵不宣称人工标记已送达、放弃、远端查询、通知中心、Portal 或通用 Connector 完成。
