---
title: M321 入站 INTEGRATION Mapping 物化验收矩阵
status: Implemented
milestone: M321
lastUpdated: 2026-07-19
---

# M321 入站 INTEGRATION Mapping 物化验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M321-01 | Bundle 无 INBOUND Mapping | 兼容旧适配器路径建单 | 既有零 Mapping OEM 冒烟 / 兼容路径 |
| M321-02 | Bundle 唯一 INBOUND Mapping | Mapping 应用 + 审计 APPLIED | `BydCpimInboundOrderHttpPostgresIT` |
| M321-03 | Mapping 字段与适配器不一致 | Mapping 值写入工单（UPPER） | `mappingUpperMaterializesExternalOrderCodeOntoWorkOrder` |
| M321-04 | mappingVersionId | 等于冻结资产 versionId | CanonicalMessage.mapping_version_id 断言 |
| M321-05 | contentDigest | 写入 Canonical JSON | payload 含 `mappingContentDigest` |
| M321-06 | Mapping 必填空白 | 失败关闭 reject | `CreateWorkOrderMappingMaterializerTest` |
| M321-07 | 多命中 Mapping | 失败关闭 | 既有 `DefaultIntegrationMappingRuntimeTest` |

## 明确不验收

- 出站 Mapping
- 默认值/枚举/条件 DSL
- 吉利真实 Sandbox
