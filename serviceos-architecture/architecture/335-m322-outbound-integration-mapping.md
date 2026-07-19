---
title: M322 出站 INTEGRATION Mapping 生成提审 Payload
status: Implemented
milestone: M322
lastUpdated: 2026-07-19
relatedMilestones: [M303, M304, M321, M297]
---

# M322 出站 INTEGRATION Mapping 生成提审 Payload

## 目标

当 Task 冻结 Bundle 含 connector 唯一 OUTBOUND INTEGRATION Mapping 时，提审创建路径用 Mapping
从内部事实生成 OEM Payload，并锁定资产 versionId / contentDigest。

## 范围与非目标

- 范围：
  - `IntegrationMappingRuntime.applyOutboundForConnectorIfPresent` / `hasOutboundMappingForConnector`
  - OUTBOUND：`internalPath` → Transform → `externalPath`
  - `DefaultOutboundDeliveryService`：零 Mapping 兼容 Profile；有 Mapping 则序列化 `externalFields`
  - `mappingVersionId` = `assetVersionId`；幂等摘要含 `contentDigest`；审计 `OUTBOUND_INTEGRATION_MAPPING_APPLIED`
- 明确不做：
  - 删除 Profile `buildSubmitPayload` 兼容层（见 **M331**）
  - Geely 专用 OUTBOUND 资产夹具强制
  - Pipeline/Connector 重建 payload
  - 默认值/枚举/条件 DSL
  - Flyway / OpenAPI 变更

## 设计要点

1. Payload 仅在创建时生成并冻结；Worker 只转发已存储字节。
2. Bundle 取自 `TaskFulfillmentContext.configurationBundleId/Digest`（工单历史冻结）。
3. 内部输入最小集：`operator` / `externalOrderCode` / `commitDate`（协议时区预格式化）。
4. 多命中、必填缺失、未知 Transform 失败关闭。

## 已实现

- OUTBOUND Mapping 运行时 + 提审创建路径接入
- 单元测试 + 冻结 Bundle PostgreSQL IT

## 明确未实现

- Profile `buildSubmitPayload` 兼容层拆除：见 **M331**
- 入站全量字段仅 Mapping、defaults/enum/condition DSL
- 吉利真实 Sandbox

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest
bash scripts/agent-verify.sh it IntegrationMappingRuntimePostgresIT
```
