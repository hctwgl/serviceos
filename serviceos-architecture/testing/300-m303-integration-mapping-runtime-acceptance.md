---
title: M303 INTEGRATION Mapping 运行时验收矩阵
status: Implemented
milestone: M303
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M303-01 | Transform 白名单 | TRIM/UPPER/DATE_ISO 生效并解释 | DefaultIntegrationMappingRuntimeTest |
| M303-02 | 必填缺失 | VALIDATION_FAILED | DefaultIntegrationMappingRuntimeTest |
| M303-03 | 冻结 Bundle 加载 | 正确 manifestDigest 可应用 | IntegrationMappingRuntimePostgresIT |
| M303-04 | 错误 digest | 失败关闭 | IntegrationMappingRuntimePostgresIT |
| M303-05 | mappingKey 缺失 | RESOURCE_NOT_FOUND | IntegrationMappingRuntimePostgresIT |
