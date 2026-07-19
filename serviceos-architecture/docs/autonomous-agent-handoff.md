---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#165：M321～M338 Draft stacked
- 本切片：**M339** UPDATE/CANCEL 强制 INBOUND Mapping（branch `cursor/m339-inbound-update-cancel-mapping-required-88d5`）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M339**
- Flyway：**127**；OpenAPI：**1.0.43**
- `baselineCommit`：`PENDING_FEATURE_COMMIT`

## 本回合完成

### M339 UPDATE/CANCEL 强制 INBOUND Mapping

- Schema `messageType`；INBOUND 选择键 `(connector, direction, messageType)`
- Update/Cancel RouteHint + Materializer + 管道强制 Mapping/审计
- 删除 BYD Update/Cancel Java Mapper；GEELY Update/Cancel RouteHint 化
- 文档：`352-m339-*` / `336-m339-*`

## 验证

```text
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest,CreateWorkOrderMappingMaterializerTest,UpdateWorkOrderMappingMaterializerTest,CancelWorkOrderMappingMaterializerTest,ConfigurationSchemaDriftTest,ArchitectureTest
bash scripts/agent-verify.sh it BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT,BydCpimInboundOrderHttpPostgresIT,GeelyInboundCreateOrderPostgresIT,ReferenceOemInboundOrderPostgresIT,DualOemInboundRegressionPostgresIT,MultiOemParallelCreateSmokePostgresIT
```

## 下一本地主线

1. 低代码深化（运行时已接入后再做）
2. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
