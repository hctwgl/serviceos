---
title: M297 出站提审 Connector SPI 与 BYD 归位
status: Implemented
milestone: M297
lastUpdated: 2026-07-19
relatedMilestones: [M58, M59, M60, M267]
---

# M297 出站提审 Connector SPI 与 BYD 归位

## 目标

将 BYD 提审执行从适配器内编排抽成通用出站 SPI/管道，使第二家车企可复用同一 attempt → 单次网络 → 技术 ACK → 本地落账生命周期，且核心域不依赖 OEM 包。

## 范围与非目标

- 范围：
  - `integration.spi` 出站契约：`OutboundSubmissionConnector`、请求/签名/传输/技术 ACK、`ConnectorFailure`；
  - `OutboundSubmissionPipeline`：短事务 attempt、事务外单次发送、响应私有存储、Delivery 状态迁移、本地 finalize；
  - `BydOutboundSubmissionConnector` + 薄 `BydReviewSubmissionTaskHandler`；
  - ArchitectureTest 与单元测试证明 SPI 存在且 BYD 行为语义保持。
- 明确不做：
  - 审核回调 SPI；
  - Update/Cancel 入站；
  - 远端状态查询；
  - INTEGRATION 资产运行时映射引擎；
  - DefaultOutboundDeliveryService 创建面全面去 BYD 常量（后续切片）；
  - OpenAPI / Flyway 变更。

## 事实源

- `decisions/ADR-085-generic-connector-spi.md`
- `decisions/ADR-086-outbound-submission-connector-spi.md`
- `architecture/13-integration-reliability.md`
- `architecture/280-m267-generic-connector-spi.md`
- `roadmap/06-multi-oem-platform-kernel-delivery-plan.md`

## 设计要点

1. **事务边界**：保持 M58——attempt 短事务 → 事务外恰好一次网络 → 结果短事务；DELIVERED 后本地 Case/Route 可安全重试。
2. **技术 ACK ≠ 业务 ACK**：管道只落技术接受/拒绝/UNKNOWN；业务审核回执仍走回调路径。
3. **UNKNOWN ≠ RETRYABLE**：传输 UNKNOWN 与协议 UNKNOWN 均不得映射为可重发 HTTP。
4. **适配器禁写领域表**：BYD 连接器只签名、发送、解释；管道写 `int_` Delivery/Attempt/Ack 并调用既有 completion。

## 已实现

- SPI 类型与 `OutboundSubmissionPipeline`；
- BYD 提审任务委托管道；
- `ArchitectureTest` 断言出站 SPI/管道文件存在；
- `OutboundSubmissionSpiTest`、`BydOutboundSubmissionConnectorTest`；
- 既有 `OutboundDeliveryQueuePostgresIT` 回归通过。

## 明确未实现

- 回调/Update/Cancel/远端查询 SPI；
- REFERENCE_OEM 出站样本；
- 创建 OutboundDelivery 命令面的 connector 注册表化；
- INTEGRATION Mapping 运行时。

## 工程证据

- 无新 Flyway；Core OpenAPI 仍 1.0.39；
- 单元测试 + ArchitectureTest + OutboundDeliveryQueuePostgresIT。

## 验证命令

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh test ArchitectureTest,OutboundSubmissionSpiTest,BydOutboundSubmissionConnectorTest
bash scripts/agent-verify.sh it OutboundDeliveryQueuePostgresIT
bash scripts/agent-verify.sh docs
```
