---
title: M322 出站 INTEGRATION Mapping 验收矩阵
status: Implemented
milestone: M322
lastUpdated: 2026-07-19
---

# M322 出站 INTEGRATION Mapping 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M322-01 | Bundle 无 OUTBOUND Mapping | 兼容 Profile `buildSubmitPayload` | 既有 ReviewCase / 出站 IT |
| M322-02 | 唯一 OUTBOUND Mapping | internal→external 映射成功 | `DefaultIntegrationMappingRuntimeTest` / Postgres IT |
| M322-03 | Transform TRIM/UPPER | 写入 externalFields | 单元测试 |
| M322-04 | assetVersionId / contentDigest | 结果携带并锁定 | Postgres IT |
| M322-05 | 提审创建路径有 Mapping | payload 来自 Mapping；mappingVersionId=assetVersionId | `DefaultOutboundDeliveryService` + 审计 APPLIED |
| M322-06 | 多命中 / 必填缺失 | 失败关闭 | 运行时既有失败关闭语义 |

## 明确不验收

- 删除 Profile 硬编码
- Geely Sandbox
- 默认值/枚举/条件 DSL
