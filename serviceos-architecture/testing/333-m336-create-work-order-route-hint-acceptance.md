---
title: M336 CREATE_WORK_ORDER RouteHint 验收矩阵
status: Implemented
milestone: M336
lastUpdated: 2026-07-19
---

# M336 CREATE_WORK_ORDER RouteHint 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M336-01 | Materializer 仅用 RouteHint + Mapping | 领域字段来自 Mapping；businessKey 可重写 | `CreateWorkOrderMappingMaterializerTest` |
| M336-02 | Geely toRouteHint | 仅路由字段；无客户/VIN 映射 | `GeelyCreateOrderMapperTest` |
| M336-03 | BYD/REF/GEELY 建单 | 仍成功；Canonical 为 Mapping 物化 | 各 OEM HTTP/IT + Multi/Dual |
| M336-04 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- Update/Cancel RouteHint、Bundle 路由完全配置化、吉利 Sandbox、OpenAPI/Flyway
