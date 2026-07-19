---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148：https://github.com/hctwgl/serviceos/pull/148 — **M321** 入站 Mapping 物化（Draft）
- PR #149（本分支）：**M322** 出站 Mapping（stacked on M321）
- 分支：`cursor/m322-outbound-integration-mapping-88d5`
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M322**
- Flyway：**120 / 122**；OpenAPI：**1.0.43**

## 本回合完成

### P0 + M321（PR #148）

- 基线文档收口；入站 Mapping 物化为建单命令

### M322（本分支）

- `IntegrationMappingRuntime` OUTBOUND：internalPath → externalPath
- `DefaultOutboundDeliveryService`：冻结 Bundle OUTBOUND Mapping 生成提审 payload
- 零 Mapping 兼容 Profile；`mappingVersionId`=`assetVersionId`；审计 APPLIED
- `IntegrationMappingResult.externalFields`

## 验证（M322）

```text
bash scripts/agent-verify.sh docs   # PASS
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest,CreateWorkOrderMappingMaterializerTest  # PASS
bash scripts/agent-verify.sh it IntegrationMappingRuntimePostgresIT  # PASS
bash scripts/agent-verify.sh arch   # PASS
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步入口

1. 合并 PR #148 再合 #149（或 squash 顺序）
2. **M323 候选**：ASSIGNEE_POLICY 自动接入 TaskAssignment prepare/activate
3. 随后 DISPATCH → RULE → NOTIFICATION → PRICING → Admin UNKNOWN/Replay 工作台

## 关键代码入口

- `DefaultIntegrationMappingRuntime.java`（OUTBOUND）
- `DefaultOutboundDeliveryService.java`
- `architecture/335-m322-outbound-integration-mapping.md`
