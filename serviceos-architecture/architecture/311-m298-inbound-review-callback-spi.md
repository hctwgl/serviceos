---
title: M298 入站审核回调 SPI 与 BYD 归位
status: Implemented
milestone: M298
lastUpdated: 2026-07-19
relatedMilestones: [M57, M267, M297]
---

# M298 入站审核回调 SPI 与 BYD 归位

## 目标

将 BYD 厂端审核回调的逐订单 Canonical/领域回执编排抽成通用管道，使适配器只负责验签、防重放、batch Envelope 与协议映射。

## 范围与非目标

- 范围：
  - `ReviewCallbackMappedItem` SPI；
  - `InboundReviewCallbackItemPipeline`（存储、登记、冲突、路由、回执、审计/Outbox）；
  - `BydCpimReviewCallbackService` 委托管道；
  - ArchitectureTest 与单元/回归 IT。
- 明确不做：
  - 非 BYD 第二家回调样本；
  - Update/Cancel 入站；
  - 远端状态查询；
  - OpenAPI / Flyway 变更。

## 事实源

- ADR-085 / ADR-086
- `architecture/13-integration-reliability.md`
- `architecture/310-m297-outbound-submission-connector-spi.md`

## 设计要点

1. **两段式保持**：transport Envelope + Nonce 仍在适配器短事务；逐订单 Canonical 在管道事务。
2. **幂等**：同 businessKey 同摘要复制结果；摘要冲突失败关闭并人工任务。
3. **路由零命中**：ROUTE_NOT_FOUND → MANUAL_TASK，不得静默成功。
4. **审计**：`InboundConnectorAuditContext` 提供 actor/authPolicy；事件类型保持 `integration.external-review-callback-processed`。

## 已实现

- SPI + 管道 + BYD 委托；
- `ReviewCallbackMappedItemTest`、ArchitectureTest、`ReviewCasePostgresIT` 回归。

## 明确未实现

- REFERENCE_OEM 回调；Update/Cancel；远端查询；创建面 connector 注册表。

## 工程证据

- 无新 Flyway；OpenAPI 仍 1.0.39。

## 验证命令

```bash
bash scripts/agent-verify.sh test ArchitectureTest,ReviewCallbackMappedItemTest,BydCpimReviewCallbackMapperTest
bash scripts/agent-verify.sh it ReviewCasePostgresIT
```
