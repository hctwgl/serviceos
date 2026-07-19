---
title: M334 入站 Mapping defaults/enum/condition DSL
status: Implemented
milestone: M334
lastUpdated: 2026-07-19
relatedMilestones: [M303, M321, M333]
---

# M334 入站 Mapping defaults/enum/condition DSL

## 目标

为 INTEGRATION Mapping 增加有界 DSL：`constantValue` / `defaultValue` / `enumMap` /
`condition`，使 brand/product 等常量由配置提供，并移除建单管道对适配器字段的播种。

## 范围与非目标

- 范围：
  - `integration-v1.schema.json`（backend + architecture 镜像）扩展字段与 source condition
  - `DefaultIntegrationMappingRuntime`：condition → constant/default/path → transform → enumMap
  - `ConfigurationAssetSchemaValidator` 语义校验（方向相关路径、条件形态）
  - `InboundCreateWorkOrderPipeline` 删除 brand/product `putIfAbsent` 播种
  - BYD HTTP / 三 OEM 冒烟夹具改用 `constantValue`
- 明确不做：
  - 任意脚本 / 复用完整 `SERVICEOS_EXPR_V1` 作 OEM 原文条件
  - 强制全部 connector 配置 INBOUND Mapping
  - 删除 OEM 入站 Java Mapper
  - Admin 可视化 DSL 编辑器
  - OpenAPI / Flyway

## 已实现

- Mapping DSL + 管道播种拆除 + 单元/IT 证据

## 明确未实现

- 嵌套表达式、错误码映射扩展
- 强制 INBOUND Mapping → 见 **M335**；OEM Mapper 拆除仍待后续
- DISPATCH 地图/比例、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest,ConfigurationSchemaDriftTest
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT,BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
