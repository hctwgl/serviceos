---
title: M321 入站 INTEGRATION Mapping 物化为建单命令
status: Implemented
milestone: M321
lastUpdated: 2026-07-19
relatedMilestones: [M303, M304, M320]
---

# M321 入站 INTEGRATION Mapping 物化为建单命令

## 目标

当冻结 Bundle 含 connector 唯一 INBOUND INTEGRATION Mapping 时，Mapping 输出不再只做校验/审计后丢弃，
而是物化为建单 Canonical 与领域命令的权威字段来源。

## 范围与非目标

- 范围：
  - `CreateWorkOrderMappingMaterializer`：Mapping 命中字段权威覆盖适配器兼容映射
  - `InboundCreateWorkOrderPipeline` 在 Mapping 应用成功后物化 `CreateWorkOrderMappedInbound`
  - `mappingVersionId` = 冻结资产 `assetVersionId`
  - Canonical JSON 嵌入 `mappingContentDigest` / `mappingKey` / 有效字段
  - BYD HTTP IT 证明 UPPER Transform 写入工单，且 Canonical 含 digest
- 明确不做：
  - 出站 OUTBOUND Mapping
  - 完全删除 OEM Java Mapper（未映射字段仍可兼容）
  - 默认值 / 枚举映射 / 条件映射 DSL 扩展
  - Update/Cancel 管道 Mapping 物化
  - 新 Flyway / OpenAPI 变更

## 事实源

- `architecture/316-m303-integration-mapping-runtime.md`
- `architecture/317-m304-create-work-order-integration-mapping-gate.md`
- ADR-085 通用 Connector SPI
- `configuration-schemas/integration-v1.schema.json`

## 设计要点

1. 零 Mapping：兼容 M304 旧路径，继续使用适配器 `CreateWorkOrderMappedInbound`。
2. 恰好一命中：执行 Mapping → 物化；命中字段覆盖适配器；`clientCode` 仍来自 Connector 常量。
3. 多命中 / 必填缺失 / 未知 Transform：失败关闭，reject Envelope（`INTEGRATION_MAPPING_FAILED`）。
4. Canonical 载荷改为 Mapping 物化 JSON，确保历史工单重放读取的是冻结 Mapping 证据，而非 OEM 原文副本。
5. 适配器仍负责验签、Envelope、协议 DTO 解析；不得直接写工单表。

## 已实现

- Mapping 物化器与建单管道主路径接入
- 单元测试 + BYD HTTP PostgreSQL IT（assetVersionId / contentDigest / UPPER 权威值）

## 明确未实现

- 全量字段仅由 Mapping 提供（无适配器 fallback）
- 默认值、枚举、条件、错误码映射扩展
- ASSIGNEE / DISPATCH / RULE / NOTIFICATION / PRICING 业务主链路接入
- 出站 Mapping 见 **M322**

## 工程证据

- Flyway：仍 120 / 122（无结构变更）
- OpenAPI：仍 1.0.43
- 测试：`CreateWorkOrderMappingMaterializerTest`、`BydCpimInboundOrderHttpPostgresIT`

## 验证命令

```bash
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest,DefaultIntegrationMappingRuntimeTest
bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT
```
