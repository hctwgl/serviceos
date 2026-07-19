---
title: M317 远端状态查询 SPI 验收矩阵
status: Implemented
milestone: M317
lastUpdated: 2026-07-19
---

# M317 远端状态查询 SPI 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| R317-01 | BYD query | NOT_SUPPORTED | `RemoteStatusQuerySpiTest#bydReportsNotSupported` |
| R317-02 | Geely stub | STILL_UNKNOWN | `RemoteStatusQuerySpiTest#geelyLocalStubDefaultsToStillUnknown` |
| R317-03 | 注册表未知版本 | RESOURCE_NOT_FOUND | `RemoteStatusQuerySpiTest#registryResolvesUniqueConnector` |
| R317-04 | 匿名 HTTP | 401 | `OutboundDeliveryControllerSecurityTest` |
| R317-05 | OpenAPI 兼容 | 1.0.41 无破坏 | `agent-verify.sh contracts` |
