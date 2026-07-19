---
title: M304 建单主路径冻结 INTEGRATION Mapping 闸门
status: Implemented
milestone: M304
lastUpdated: 2026-07-19
relatedMilestones: [M267, M303]
---

# M304 建单主路径冻结 INTEGRATION Mapping 闸门

## 目标

在 `InboundCreateWorkOrderPipeline` Bundle 解析成功后，若冻结 Bundle 含该 connector 的唯一 INBOUND INTEGRATION Mapping，则对 OEM 原文施加 Mapping 校验并审计版本/Digest。

## 范围

- `IntegrationMappingRuntime.hasInboundMappingForConnector` / `applyInboundForConnectorIfPresent`
- 建单管道可选 `externalSourcePayload`；零 Mapping 兼容旧 Bundle
- BYD / REFERENCE_OEM 传入原文 Map
- BYD HTTP IT 证明 `INBOUND_INTEGRATION_MAPPING_APPLIED` 审计

## 明确未实现

- ASSIGNEE_POLICY 运行时自动接入（入站物化见 **M321**；出站 Mapping 见 **M322**）

## 验证

```bash
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest
bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT
```
