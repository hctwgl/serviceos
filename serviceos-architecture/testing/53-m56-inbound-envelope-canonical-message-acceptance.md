---
title: M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 验收矩阵
version: 1.0.0
status: Implemented
---

# M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M56-INB-001 | P0 | 合法 BYD 创建工单请求 | Envelope、CanonicalMessage、WorkOrder、审计和事件完整，原始字节摘要精确 |
| M56-INB-002 | P0 | 同 transport key 与同摘要重放 | 返回首次不可变结果，不重复 Envelope、Canonical、WorkOrder、审计或事件 |
| M56-INB-003 | P0 | 同 transport key 不同摘要 | `REPLAY_CONFLICT`，不创建第二 Envelope 或覆盖首次事实 |
| M56-CAN-001 | P0 | 新 nonce、同业务键和同规范摘要 | 两个 Envelope 关联一个 CanonicalMessage/WorkOrder，第二请求为幂等重放 |
| M56-CAN-002 | P0 | 新 nonce、同业务键但不同规范摘要 | 第二 Envelope REJECTED；首次 CanonicalMessage/WorkOrder 保持不变 |
| M56-REC-001 | P0 | Envelope 已登记后 WorkOrder 事务失败 | Envelope 保持 RECEIVED；无半成 Canonical/WorkOrder/审计/事件；同 transport 重试完成 |
| M56-REJ-001 | P0 | 验签有效但映射或配置无效 | 原文留存，Envelope 固化 REJECTED，nonce 消费，不产生 Canonical/WorkOrder |
| M56-SEC-001 | P0 | 签名或时间窗无效 | 拒绝且不注册 Envelope、replay guard 或原文对象 |
| M56-SEC-002 | P0 | 匿名、缺 capability 或越 scope 查询摘要 | 401/403；合法请求也不暴露原文对象引用、签名或凭据 |
| M56-OBJ-001 | P0 | 私有对象同 key 重写 | 只有完全相同长度与 SHA-256 可幂等；不同内容失败关闭 |
| M56-DB-001 | P0 | V055 空库迁移 | PostgreSQL 18 原生镜像完成 57 个迁移并到达 v055，约束、FK、唯一键和不可变 trigger 生效 |
| M56-CON-001 | P0 | OpenAPI/事件/客户端 | 0.29.0 与 `integration.canonical-message-processed@v1` 可解析、可生成、可重复，兼容门禁通过 |
| M56-MOD-001 | P0 | 模块边界 | integration 只通过 files SPI、workorder/configuration/project/authorization/audit/reliability 公开边界协作 |

## 自动化证据映射

| 场景 | 证据入口 |
|---|---|
| INB-001～003、CAN-001～002、REC-001、REJ-001、SEC-001、DB-001 | `BydCpimInboundOrderHttpPostgresIT` + `BydCpimReplayGuardPostgresIT` + V055 + PostgreSQL 18 Testcontainers |
| SEC-002 | `InboundMessageControllerSecurityTest` + `DefaultInboundMessageQueryServiceTest` |
| OBJ-001 | `LocalPrivateObjectStorageGatewayTest` |
| CON-001 | `ContractValidationTest`、`EventSchemaGovernanceTest`、`GeneratedClientContractTest`、兼容与生成复现脚本 |
| MOD-001 | `ArchitectureTest` / `ApplicationModules.verify()` |

其他 CPIM 消息、OutboundDelivery、正式 Connector 客户端、生产对象存储和 Portal 不属于本矩阵；没有这些证据时
不得把 M56 外推为完整外部集成或完整现场履约平台。
