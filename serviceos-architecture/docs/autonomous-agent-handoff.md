---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#164：M321～M337 Draft stacked
- PR #165：https://github.com/hctwgl/serviceos/pull/165 — **M338** DISPATCH 比例缺口（Draft，base=#164）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M338**
- Flyway：**127**；OpenAPI：**1.0.43**

## 本回合完成

### M338 DISPATCH 签约比例缺口

- Flyway `V127`：`dsp_network_allocation_target`
- Target/Actual 查询（ORDER_COUNT / MONTH）；Consumer 填 gap
- Runtime：`allocationRatio` 门禁；仅 ORDER_COUNT
- 文档：`351-m338-*` / `335-m338-*`

### M337（同会话先交付）

- PR #164：https://github.com/hctwgl/serviceos/pull/164 — ServiceCoverage 地图

## 验证

```text
bash scripts/agent-verify.sh test DefaultDispatchRuntimeTest
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## 下一本地主线

1. Update/Cancel Mapping 强制 / Mapper 拆除
2. 低代码深化（运行时已接入后再做）
3. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
