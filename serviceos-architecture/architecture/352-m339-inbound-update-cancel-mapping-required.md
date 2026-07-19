---
title: M339 UPDATE/CANCEL 强制 INBOUND Mapping
status: Implemented
milestone: M339
lastUpdated: 2026-07-19
relatedMilestones: [M335, M336, M333, M334]
---

# M339 UPDATE/CANCEL 强制 INBOUND Mapping

## 目标

`InboundUpdateWorkOrderPipeline` / `InboundCancelWorkOrderPipeline` 对
UPDATE_WORK_ORDER / CANCEL_WORK_ORDER 强制要求工单冻结 Bundle 中
`(connectorCode, INBOUND, messageType)` 唯一 INTEGRATION Mapping；缺失则失败关闭。
同一 Bundle 可共存 CREATE + UPDATE + CANCEL Mapping。

## 范围与非目标

- 范围：
  - Schema `messageType`；INBOUND 必填；运行时按 messageType 选择
  - `ExternalWorkOrderPointer` 携带冻结 Bundle 引用
  - Update/Cancel RouteHint + Mapping Materializer + 管道强制 Mapping/审计
  - 删除 BYD Update/Cancel Java Mapper；GEELY Update/Cancel 去内联领域装配
  - BYD/GEELY Update/Cancel IT 夹具补齐多 Mapping
- 明确不做：
  - REFERENCE_OEM Update/Cancel
  - OpenAPI / Flyway
  - 吉利 Sandbox 联调

## 已实现

- messageType 共存选择；UPDATE/CANCEL 强制 Mapping；OEM 适配器 RouteHint 化

## 明确未实现

- REFERENCE_OEM Update/Cancel、低代码 Admin Mapping UI、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest,CreateWorkOrderMappingMaterializerTest,UpdateWorkOrderMappingMaterializerTest,CancelWorkOrderMappingMaterializerTest,ConfigurationSchemaDriftTest,ArchitectureTest
bash scripts/agent-verify.sh it BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT,BydCpimInboundOrderHttpPostgresIT,GeelyInboundCreateOrderPostgresIT,ReferenceOemInboundOrderPostgresIT,DualOemInboundRegressionPostgresIT,MultiOemParallelCreateSmokePostgresIT
```
