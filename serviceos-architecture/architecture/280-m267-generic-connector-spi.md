---
title: M267 通用 Connector SPI 与 BYD 入站边界归位
status: Implemented
milestone: M267
lastUpdated: 2026-07-18
relatedMilestones: [M16, M56, M57, M58]
---

# M267 通用 Connector SPI 与 BYD 入站边界归位

## 目标

建立多车企平台内核的工程起点：Accepted 程序验收标准、通用 CREATE_WORK_ORDER 入站 SPI/管道，以及 BYD 适配器在不改变外部契约前提下委托该管道；用架构门禁防止核心域依赖 OEM 适配包。

## 范围与非目标

- 范围：
  - `roadmap/06` 与程序级验收矩阵；
  - ADR-085；
  - `integration.spi` + `InboundCreateWorkOrderPipeline`；
  - `BydCpimInboundOrderService` 委托管道；
  - 核心模块禁止依赖 `integration.byd` 的架构测试。
- 明确不做：
  - REFERENCE_OEM / 真实第二家 sandbox；
  - BYD 审核回调与 OutboundDelivery 全面迁入 SPI；
  - 条件 Transition / EXCLUSIVE_GATEWAY / WAIT_EVENT 运行时；
  - 配置草稿审批 UI；
  - OpenAPI/Flyway 变更（本切片无契约与迁移变化）。

## 事实源

- `roadmap/06-multi-oem-platform-kernel-delivery-plan.md`
- `testing/multi-oem-platform-kernel-program-acceptance.md`
- `decisions/ADR-085-generic-connector-spi.md`
- `architecture/13-integration-reliability.md`
- `architecture/29-configuration-byd-work-order-intake-implementation.md`
- `integration/01-byd-cpim-v731-adapter-contract.md`

## 设计要点

1. **事务边界**：保持 M56 两段式——Envelope/Nonce 先独立提交；Canonical + WorkOrder + 审计 + Outbox 在管道本地事务。管道接收已登记 Envelope，不吞掉 BYD 协议层防重放。
2. **幂等键**：transport dedup key（Envelope）与 Canonical business key 仍由适配器提供；同键不同摘要失败关闭。
3. **配置锁定**：管道内 `ConfigurationService.resolve` 零/多命中失败关闭并 reject Envelope。
4. **审计**：适配器提供 actor/authPolicy；管道写入 `INBOUND_MESSAGE_PROCESSED` / `REJECTED`。

## 已实现

- `integration.spi`：`ConnectorIdentity`、`CreateWorkOrderMappedInbound`、`InboundConnectorAuditContext`、`InboundCreateWorkOrderResult`；
- `InboundCreateWorkOrderPipeline`：Bundle 解析、Canonical 存储、CanonicalMessage、领域建单、完成 Envelope、Outbox/审计；
- BYD CREATE_WORK_ORDER 入站委托管道，外部 CPIM 契约与验签/Nonce 行为保持；
- `ArchitectureTest`：Modulith 边界 + SPI 存在性 + 核心域禁止 import OEM 适配包；
- 多车企程序交付计划与验收矩阵 Accepted。

## 明确未实现

- Outbound Connector SPI；
- 回调/其他 CPIM messageType 通用化；
- REFERENCE_OEM；
- 网关运行时与配置治理 MVP。

## 工程证据

- 无新 Flyway；Core OpenAPI / BYD OpenAPI 不变；
- `InboundCreateWorkOrderPipelineTest`、`ArchitectureTest`；
- `BydCpimInboundOrderHttpPostgresIT`、`BydCpimReplayGuardPostgresIT`；
- 相关 BYD 单测与 `InboundMessageControllerSecurityTest`。

## 验证命令

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh test ArchitectureTest,InboundCreateWorkOrderPipelineTest
bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,BydCpimReplayGuardPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh docs
```
