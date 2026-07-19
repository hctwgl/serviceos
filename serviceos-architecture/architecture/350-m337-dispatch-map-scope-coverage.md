---
title: M337 DISPATCH 地图 scope 与 ServiceCoverage
status: Implemented
milestone: M337
lastUpdated: 2026-07-19
relatedMilestones: [M324, M332, M306]
---

# M337 DISPATCH 地图 scope 与 ServiceCoverage

## 目标

NETWORK 自动派单候选必须经网点 **ServiceCoverage**（品牌/业务/行政区）过滤；
`DispatchRuntime` 校验 policy.scope 与工单相交，并对候选 `REGION_SCOPE` 按省/市/区任一码命中。

## 范围与非目标

- 范围：
  - Flyway `V126`：`net_service_network_coverage`
  - `ServiceNetworkCoverageQuery` / View + JDBC 查询
  - `DefaultTaskDispatchPolicyEventConsumer`：项目网点 ∩ ACTIVE ∩ Coverage ∩ 容量
  - `DefaultDispatchRuntime`：policy.scope 门禁；REGION_SCOPE 省市区任一命中
  - IT：覆盖缺失失败关闭、同城弱网优先于异地高容量
- 明确不做：
  - 比例分配（ALLOCATION_RATIO）闭环
  - 师傅级 Coverage / TECHNICIAN 地图
  - Admin 覆盖维护 UI、Coverage 写 API
  - OpenAPI 变更

## 已实现

- 覆盖表 + 查询端口；NETWORK 候选仅含 ACTIVE 覆盖且 region 与工单省/市/区精确匹配的网点
- 无覆盖 / 无容量 → `SERVICE_DISPATCH_POLICY_MANUAL`，不写 NETWORK
- policy.scope 不匹配 → `POLICY_SCOPE_MISMATCH` + MANUAL
- 单元 + `DispatchPolicyServiceAssignmentPostgresIT` + ArchitectureTest

## 明确未实现

- 比例分配、师傅 Coverage、Admin 地图编辑、自动改派地图重算

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultDispatchRuntimeTest
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
