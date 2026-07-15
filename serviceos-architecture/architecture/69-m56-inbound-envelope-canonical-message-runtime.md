---
title: M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 权威入站事实
version: 1.0.0
status: Implemented
---

# M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 权威入站事实

## 1. 决策基线

本切片把 M16 已存在的 BYD CPIM V7.3.1 创建工单入口推进为可恢复、可审计的权威入站链路，
落实 [集成可靠性设计](13-integration-reliability.md) 中 Envelope 与 CanonicalMessage 的最小可靠语义。
它只收敛已有 BYD 安装订单协议，不代表 ADR-010/ADR-014 的全部提案已接受或所有 Connector 已完成。

## 2. 已实现范围

1. 只有通过 BYD 签名与时间窗校验的请求才注册 `InboundEnvelope`；签名失败不留业务事实；
2. transport dedup key、nonce、原文摘要与 Envelope 在同一事务登记，重复 transport key 只返回首次结果，
   同 key 不同内容失败关闭；
3. HTTP 原始字节按 SHA-256 精确校验后写入服务端私有对象存储；数据库和普通查询不返回原文对象引用、
   签名或凭据；
4. BYD 反腐映射生成 `CanonicalMessage`，冻结 connector、messageType、businessKey、映射版本、规范载荷摘要
   和源 Envelope；
5. 同一业务键与同一规范摘要只生成一个 CanonicalMessage 和一个 WorkOrder；同一业务键不同摘要拒绝，
   不覆盖首次事实；
6. CanonicalMessage 完成、WorkOrder 结果、Envelope 完成、审计、Outbox 和 replay result 在领域事务内原子提交；
7. Envelope 先独立提交；进程在原文落盘或领域事务前后退出时，相同 transport 请求继续同一 RECEIVED 记录，
   不猜测成功；
8. 认证用户须经 tenant/project scope 与 `integration.readInbound` capability 才能读取 Envelope/Canonical 摘要；
9. 首次成功产生 `integration.canonical-message-processed@v1`，重复请求不重复发布。

## 3. 状态、数据与契约

- `InboundEnvelope.processingStatus`：`RECEIVED -> COMPLETED|REJECTED`，终态不可改写；
- `CanonicalMessage.processingStatus`：`PROCESSING -> COMPLETED`，标识和完成结果不可改写；
- Flyway **V055** 创建 `int_inbound_envelope`、`int_canonical_message`，并把既有 replay guard 关联到 Envelope；
- OpenAPI **0.29.0** 新增 `GET /api/v1/inbound-envelopes/{id}` 与
  `GET /api/v1/canonical-messages/{id}` 安全摘要；
- 事件新增不可变 `integration.canonical-message-processed@v1`；
- 当前空库迁移基线为 **v055 / 57 个迁移**。

V055 对历史 replay guard 行保留 nullable Envelope 外键，仅用于已存在数据的结构迁移；M56 运行时注册或重放
若缺少 Envelope 关联会失败关闭，不提供回退、默认推断或双轨读取。

## 4. 事务、恢复与失败语义

链路分为两个明确事务边界：

1. 验签后注册 Envelope 与 replay guard，成功后才能写私有原文；
2. 注册 CanonicalMessage、执行 WorkOrder 命令，并提交 Canonical/Envelope 完成、审计、Outbox 与响应摘要。

第一事务使崩溃恢复有稳定锚点；第二事务保证不会出现“工单成功但入站记录或事件丢失”。对象写入使用内容寻址、
精确长度和摘要校验，同一对象键只允许相同内容。业务校验或配置解析失败把 Envelope 固化为 REJECTED 并消费
该 nonce；基础设施异常不伪造拒绝或成功，由相同 transport 请求恢复。

## 5. 明确未实现

- 外部审核回调、取消、勘测、安装资料等其他 CPIM messageType 的通用标准化；
- callbackBatchRef / mappingVersionId 与 M55 CLIENT ReviewCase 的 integration 域权威关联；
- OutboundDelivery、Connector 网络客户端、凭据轮换、自动重试、查询远端状态与人工重放；
- 文件批次/SFTP、Ack/Replay 聚合、原文授权下载工作流；
- 正式对象存储、生产 BYD sandbox/凭据与脱敏真实流量演练；
- Integration Portal、异常工作台联动和全车企连接器目录。

不得把 M56 的单一 BYD 创建工单切片外推为完整车企集成闭环或完整现场履约平台。

## 6. 自动化证据

证据见 [M56 验收矩阵](../testing/53-m56-inbound-envelope-canonical-message-acceptance.md)。核心入口为
`BydCpimInboundOrderHttpPostgresIT`、`BydCpimReplayGuardPostgresIT`、
`LocalPrivateObjectStorageGatewayTest`、`DefaultInboundMessageQueryServiceTest`、
`InboundMessageControllerSecurityTest`、契约/客户端门禁和 `ArchitectureTest`。
