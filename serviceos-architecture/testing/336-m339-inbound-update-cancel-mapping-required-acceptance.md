---
title: M339 UPDATE/CANCEL 强制 INBOUND Mapping 验收矩阵
status: Implemented
milestone: M339
lastUpdated: 2026-07-19
---

# M339 UPDATE/CANCEL 强制 INBOUND Mapping 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M339-01 | Schema INBOUND 缺 messageType | 发布/漂移失败关闭 | `ConfigurationSchemaDriftTest` + integration-v1.schema |
| M339-02 | 同 Bundle CREATE+UPDATE 按 messageType 选择 | 各自命中唯一 Mapping | `DefaultIntegrationMappingRuntimeTest#selectsInboundMappingByConnectorCodeAndMessageType` |
| M339-03 | UPDATE Materializer | 必填字段 + digest businessKey | `UpdateWorkOrderMappingMaterializerTest` |
| M339-04 | CANCEL Materializer | reasonCode + key rebuild | `CancelWorkOrderMappingMaterializerTest` |
| M339-05 | BYD create→update | 地址变更 + Mapping 审计；重放安全 | `BydCpimUpdateOrderHttpPostgresIT` |
| M339-06 | BYD create→cancel | EXTERNAL_USER_CANCEL；重放安全 | `BydCpimCancelOrderHttpPostgresIT` |
| M339-07 | GEELY create→update→close | 联系人/地址更新后取消 | `GeelyInboundCancelUpdatePostgresIT` |
| M339-08 | 既有 CREATE 夹具含 messageType | 建单仍绿 | BYD/GEELY/REF/Dual/Multi Create IT |
| M339-09 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- REFERENCE_OEM Update/Cancel、OpenAPI/Flyway、吉利 Sandbox
