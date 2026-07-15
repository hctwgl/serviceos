---
title: M58 BYD 提审 OutboundDelivery 验收矩阵
version: 1.0.0
status: Implemented
---

# M58 BYD 提审 OutboundDelivery 验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M58-CMD-001 | P0 | 已通过 INTERNAL Case 创建提审 | 精确冻结 Snapshot/Task/WorkOrder/Canonical lineage、orderCode、decidedBy 和 payload digest |
| M58-CMD-002 | P0 | 重复业务命令 | 同一 source Case 只有一个 Delivery 和执行 Task，payload 不改写 |
| M58-CMD-003 | P0 | USER、缺 capability、跨 project 或非 INTERNAL/非已通过 Case | 401/403/409 失败关闭，无交付副作用 |
| M58-PAY-001 | P0 | 冻结 BYD 提审请求 | 仅允许 operatePerson/orderCode/commitDate，时间格式和长度精确，未知字段拒绝 |
| M58-NET-001 | P0 | 一次正常发送且 `errno=0` | 短事务登记 Attempt，网络在事务外，响应私有留存，Delivery 先 DELIVERED |
| M58-NET-002 | P0 | CPIM `errno != 0` | 不可变 REJECTED acknowledgement，Task 最终失败，不重发 |
| M58-NET-003 | P0 | 配置/payload/签名在发送前失败 | FAILED_FINAL，网络调用次数为 0 |
| M58-UNK-001 | P0 | 超时、断连、非 2xx、非法响应或响应存储失败 | UNKNOWN 持久化，自动 Task 不得重发，开启 OperationalException + HUMAN Task |
| M58-UNK-002 | P0 | 进程在 SENDING 后中断且 Task lease 最终转人工 | 本地 Outbox 消费将对应 Attempt/Delivery 收敛为 UNKNOWN，不留可自动重试的 PENDING |
| M58-FIN-001 | P0 | 明确送达后本地落账 | 同事务幂等创建 CLIENT Case、登记 M57 Route、ACKNOWLEDGED 及 Outbox/审计 |
| M58-FIN-002 | P0 | DELIVERED 后本地落账首次失败 | Task 只重试本地落账，HTTP 调用始终为 1 |
| M58-SEC-001 | P0 | Delivery 摘要查询 | 实时 tenant/project scope；不暴露 app secret、nonce、签名、原文或对象引用 |
| M58-DB-001 | P0 | V058 空库迁移 | PostgreSQL 18 原生镜像到 v058/60；唯一约束、状态检查、条件更新和不可变 trigger 生效 |
| M58-CON-001 | P0 | Core/BYD OpenAPI、外部 payload、事件 Schema 与客户端 | 全部可解析、有效样例通过、客户端可重复生成、兼容门禁通过 |
| M58-MOD-001 | P0 | 模块边界 | integration 仅通过 evidence/files/reliability/task/operations 公开 API/SPI 协作，Modulith 通过 |

## 自动化证据映射

- `ReviewCasePostgresIT#bydReviewSubmission*`：M58-CMD/PAY/NET/UNK/FIN 与跨模块事务结果；
- `HttpBydCpimSubmitReviewGatewayTest`：M58-NET 的路径、签名头、超限响应和非 2xx 边界；
- `OutboundDeliveryControllerSecurityTest`：M58-CMD-003 和 M58-SEC-001；
- Flyway PostgreSQL IT、`ContractValidationTest`、`EventSchemaGovernanceTest`、
  `GeneratedClientContractTest`、客户端可重复生成与 `ArchitectureTest`：M58-DB/CON/MOD。

本矩阵不宣称人工 UNKNOWN 处置命令、其他 CPIM 消息、通用 Connector、生产凭据/
对象存储、真实 sandbox、Evidence target 自动映射或 Portal 已完成。
