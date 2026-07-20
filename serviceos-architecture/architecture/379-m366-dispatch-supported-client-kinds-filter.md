---
title: M366 派单级 supportedClientKinds 过滤
status: Implemented
milestone: M366
lastUpdated: 2026-07-20
relatedMilestones: [M358, M359, M332, M324, M356]
openapiVersion: "1.0.58"
flywayVersion: "134"
---

# M366 派单级 supportedClientKinds 过滤

## 状态

**Implemented**。承接已接受 ADR-088（`A1-R, A2-R, A3-R, A4-R, A5-R`）。

## 目标

在自动 TECHNICIAN 指派时刻按冻结 Bundle 的 FORM/EVIDENCE `supportedClientKinds` 硬过滤候选，
避免“先派错端、再在 Portal 422”。

## 已实现范围

1. **A2-R** Flyway V134：`net_technician_profile.supported_client_kinds`；创建/声明 API；
   OpenAPI 1.0.58（`TechnicianProfile.supportedClientKinds` +
   `declareTechnicianSupportedClientKinds`）；
2. **A4-R** `FrozenBundleClientCapabilityProbe.resolveDispatchTargetClientKinds`：
   FORM（`formRef`）∩ 全部 EVIDENCE 定向交集；全未定向 → 不施加派单 kind 过滤；
3. **A1-R/A3-R** `DefaultTaskDispatchPolicyEventConsumer.activateTechnician`：
   仅自动池硬过滤；过滤后为空 → `SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL` +
   `error_code=CLIENT_KIND_TARGET_EMPTY`；保留 NETWORK；
4. **A5-R** 不删除 M357～M363 执行门禁。

## 明确未实现

- Manual / Network Portal assign/reassign 硬拒绝（A1-B）；
- Network Portal on-behalf / `NETWORK_WEB` 代师傅语义；
- iOS 条件执行器全量硬阻断；
- clientVersion 下限；跨区回退；改写 ADR-009 评分模型。

## 验证

```bash
bash scripts/agent-verify.sh test DispatchClientKindMatchTest,DefaultFrozenBundleClientCapabilityProbeTest
bash scripts/agent-verify.sh it DispatchClientKindFilterPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh docs
```
