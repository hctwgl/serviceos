---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#160：M321～M333 Draft stacked
- 本切片分支：`cursor/m334-inbound-mapping-default-enum-condition-88d5` — **M334** Mapping DSL（Draft，base=#160）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M334**
- Flyway：**125**；OpenAPI：**1.0.43**

## 本回合完成

### M334 入站 Mapping defaults/enum/condition DSL

- Schema：`constantValue` / `defaultValue` / `enumMap` / `condition`
- Runtime：condition → constant/default/path → transform → enumMap
- 移除 `InboundCreateWorkOrderPipeline` brand/product 播种
- BYD/MultiOem 夹具改用 constantValue
- 文档：`347-m334-*` / `331-m334-*`

### 既有 Draft 栈

- M321～M333（PR #148～#160）

## 验证

```text
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest,ConfigurationSchemaDriftTest
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT,BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步

本地 Configuration-Driven Runtime 主线已交付至 M334 Draft 栈。后续优先：

1. 强制全部 connector INBOUND Mapping / 删除 OEM 入站 Java Mapper
2. DISPATCH 地图/比例分配；低代码深化
3. 吉利材料齐备后联调
