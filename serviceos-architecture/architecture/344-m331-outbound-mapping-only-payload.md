---
title: M331 出站提审仅 Mapping 生成 Payload
status: Implemented
milestone: M331
lastUpdated: 2026-07-19
relatedMilestones: [M322, M316, M330]
---

# M331 出站提审仅 Mapping 生成 Payload

## 目标

删除 `OutboundReviewSubmissionProfile.buildSubmitPayload` 兼容层；提审创建必须由
Task 冻结 Bundle 的唯一 OUTBOUND INTEGRATION Mapping 生成 OEM Payload。

## 范围与非目标

- 范围：
  - `DefaultOutboundDeliveryService`：零 Mapping / 无冻结 Bundle → `VALIDATION_FAILED`
  - 从 SPI / BYD / Geely Profile 删除 `buildSubmitPayload` 与 `outboundMappingVersion`
  - `mappingVersionId` 始终为 Mapping 资产 `versionId`
  - ReviewCase IT 夹具补齐 BYD OUTBOUND Mapping
- 明确不做：
  - 入站「全量字段仅 Mapping」/ 删除 OEM 入站 Mapper
  - defaults / enum / condition DSL
  - Geely Sandbox 真实联调
  - OpenAPI / Flyway 变更

## 已实现

- 出站提审失败关闭仅 Mapping + Profile SPI 瘦身 + IT 夹具

## 明确未实现

- 入站 adapter fallback 拆除、Mapping DSL、DISPATCH TECHNICIAN

## 验证命令

```bash
bash scripts/agent-verify.sh test OutboundReviewSubmissionProfilesTest,GeelyOutboundSubmissionConnectorTest
bash scripts/agent-verify.sh it ReviewCasePostgresIT,IntegrationMappingRuntimePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
