---
title: M304 建单 INTEGRATION Mapping 闸门验收矩阵
status: Implemented
milestone: M304
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M304-01 | connector 选择 | hasInboundMappingForConnector 零/一命中 | DefaultIntegrationMappingRuntimeTest |
| M304-02 | 建单含 Mapping | 成功建单并审计 APPLIED | BydCpimInboundOrderHttpPostgresIT |
| M304-03 | 无 Mapping Bundle | 旧路径仍可用 | IntegrationMappingRuntimePostgresIT / 兼容路径 |
| M304-04 | Mapping 多命中 | INTERNAL_ERROR 失败关闭 | DefaultIntegrationMappingRuntime（多命中分支） |
