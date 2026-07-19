---
title: M334 入站 Mapping defaults/enum/condition DSL 验收矩阵
status: Implemented
milestone: M334
lastUpdated: 2026-07-19
---

# M334 入站 Mapping defaults/enum/condition DSL 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M334-01 | constantValue 提供 brand/product | 无播种输入仍写出内部字段 | `DefaultIntegrationMappingRuntimeTest` |
| M334-02 | defaultValue 补空源 | 缺省源路径使用 default | 同上 |
| M334-03 | enumMap 命中/未命中 | 命中规范化；未命中失败关闭 | 同上 |
| M334-04 | condition false | 跳过映射且不强制 required | 同上 |
| M334-05 | schema drift | backend/architecture schema 一致 | `ConfigurationSchemaDriftTest` |
| M334-06 | BYD HTTP 常量 Mapping | 建单成功；无管道播种 | `BydCpimInboundOrderHttpPostgresIT` |
| M334-07 | 三 OEM 并行冒烟 | BYD constant brand/product 成功 | `MultiOemParallelCreateSmokePostgresIT` |
| M334-08 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- 完整表达式引擎、Admin DSL UI、强制全部 connector INBOUND、OEM Mapper 拆除、吉利联调
