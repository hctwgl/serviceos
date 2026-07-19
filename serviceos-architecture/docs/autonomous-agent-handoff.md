---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#159：M321～M332 Draft stacked
- PR #160：https://github.com/hctwgl/serviceos/pull/160 — **M333** 入站 Mapping 无适配器 fallback（Draft，base=#159）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M333**
- Flyway：**125**；OpenAPI：**1.0.43**

## 本回合完成

### M333 入站 Mapping 物化无适配器 fallback

- `CreateWorkOrderMappingMaterializer`：命中 INBOUND 后建单字段仅 Mapping；可选省略 → null
- `clientCode` 仍来自 connector；`businessKey` 按 Mapping `externalOrderCode` 重写
- Pipeline：Mapping 输入临时播种 brand/product（待 defaults DSL）
- 零 Mapping 路径保留
- IT：`CreateWorkOrderMappingMaterializerTest`、`BydCpimInboundOrderHttpPostgresIT`、
  `MultiOemParallelCreateSmokePostgresIT`
- 文档：`346-m333-*` / `330-m333-*`

### 既有 Draft 栈

- M321～M332（PR #148～#159）

## 验证

```text
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT,BydCpimInboundOrderHttpPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步

本地 Configuration-Driven Runtime 主线已交付至 M333 Draft 栈。后续优先：

1. Mapping：defaults / enum / condition DSL（移除 brand/product 播种）
2. 强制全部 connector INBOUND Mapping / 删除 OEM 入站 Java Mapper
3. DISPATCH 地图/比例分配；低代码深化
4. 吉利材料齐备后联调
