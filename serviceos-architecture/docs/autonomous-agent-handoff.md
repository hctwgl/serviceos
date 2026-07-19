---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#163：M321～M336 Draft stacked
- PR #PENDING：`cursor/m337-dispatch-map-scope-coverage-88d5` — **M337** DISPATCH ServiceCoverage 地图（Draft，base=#163）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M337**
- Flyway：**126**；OpenAPI：**1.0.43**

## 本回合完成

### M337 DISPATCH 地图 scope / ServiceCoverage

- Flyway `V126`：`net_service_network_coverage`
- `ServiceNetworkCoverageQuery`：ACTIVE 覆盖按 brand/business/时间窗查询
- NETWORK 候选 = 项目网点 ∩ ACTIVE ∩ Coverage（省/市/区精确匹配）∩ 容量
- `DefaultDispatchRuntime`：policy.scope 门禁；REGION_SCOPE 省市区任一命中
- 无覆盖失败关闭 MANUAL；同城弱网优先于异地高容量
- 文档：`350-m337-*` / `334-m337-*`

### 既有 Draft 栈

- M321～M336（PR #148～#163）

## 验证

```text
bash scripts/agent-verify.sh test DefaultDispatchRuntimeTest
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## 下一本地主线

1. DISPATCH 比例分配
2. Update/Cancel Mapping 强制 / Mapper 拆除
3. 低代码深化（运行时已接入后再做）
4. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
