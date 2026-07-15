---
module: integration
status: Partial
lastVerifiedMilestone: M60
---

# integration 模块卡片

## 事实所有权

- Connector 协议适配、InboundEnvelope、CanonicalMessage；
- OutboundDelivery、Attempt、Acknowledgement 和外部路由；
- 外部报文验签、防重放、映射版本和可靠交付结果。

Integration 不拥有 WorkOrder、ReviewCase、Evidence、Task 或 OperationalException 的内部状态。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/integration/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/integration/`；
- 契约：`serviceos-contracts/src/main/resources/openapi/` 与外部/事件 Schema；
- 车企 DTO 必须在 Anti-Corruption Layer 内终止。

## 必读事实源

- `serviceos-architecture/architecture/13-integration-reliability.md`；
- M56～M60 实现文档和验收矩阵；
- BYD CPIM OpenAPI 与对应 JSON Schema；
- `serviceos-architecture/architecture/20-transaction-messaging-concurrency-blueprint.md`。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg '(BydCpim|Inbound|OutboundDelivery|ExternalReview).*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
serviceos-contracts/scripts/check-contract-compatibility.sh
```

外部副作用、重放、UNKNOWN、严格 ACK 和恢复必须包含 PostgreSQL IT 与协议契约证据。

## 相邻模块

- 上游：reliability、configuration；
- 下游：workorder、review、task、operations；
- 新消息类型只展开实际消费或生产该消息的领域模块。

## 稳定不变量

- 验签和时间窗失败不留下伪造成功；
- transport 重放与业务幂等分别处理；
- 外部网络调用不位于持有数据库锁的长事务；
- UNKNOWN 不得自动当作成功或自动重发；
- 已发布外部协议和事件 Schema 不得原地破坏。

## 扩大检索触发条件

新车企/消息类型、签名语义、凭据、文件批次、外部重试、人工处置、公开契约或跨模块路由变化。
