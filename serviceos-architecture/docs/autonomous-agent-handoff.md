---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/148（Draft）
- 分支：`cursor/m321-integration-mapping-mainpath-88d5`
- HEAD：见分支 tip；功能证据 `e6bb6cb8`；status 回填提交随后
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 **已合并**）
- latestMilestone：**M321**
- Flyway：**120 / 122**；OpenAPI：**1.0.43**

## 本回合完成

### P0 基线收口

- `baselineCommit` / README / status 下一方向对齐 Configuration-Driven Fulfillment Runtime
- Notification（M307）、Pricing（M309）状态改为 `PARTIAL`
- handoff 标记 PR #147 已合并；删除过期「远端查询 SPI 未实现 / 下一主线 P3」描述

### M321 入站 Mapping 物化

- `CreateWorkOrderMappingMaterializer` + `InboundCreateWorkOrderPipeline` 主路径
- Mapping 命中字段权威；`mappingVersionId`=`assetVersionId`；Canonical 嵌 `mappingContentDigest`
- 零 Mapping 兼容旧路径；OEM Mapper 降级为未映射字段兼容层

## 验证

```text
bash scripts/agent-verify.sh docs   # PASS
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest,DefaultIntegrationMappingRuntimeTest,InboundCreateWorkOrderPipelineTest  # PASS
bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT  # PASS
bash scripts/agent-verify.sh arch   # 进行中/见后续
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT  # 进行中/见后续
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步入口

1. 合并 PR #148 后将 `baselineCommit` 改为 master 合并提交
2. **M322 候选**：出站 OUTBOUND INTEGRATION Mapping → OEM Payload → Connector
3. 随后 ASSIGNEE_POLICY 自动 TaskAssignment（P2）

## 关键代码入口

- `serviceos-backend/.../CreateWorkOrderMappingMaterializer.java`
- `serviceos-backend/.../InboundCreateWorkOrderPipeline.java`
- `serviceos-architecture/architecture/334-m321-inbound-integration-mapping-materialization.md`
