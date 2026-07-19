---
title: M337 DISPATCH 地图 scope 与 ServiceCoverage 验收矩阵
status: Implemented
milestone: M337
lastUpdated: 2026-07-19
---

# M337 DISPATCH 地图 scope 与 ServiceCoverage 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M337-01 | 默认覆盖城市 + 容量 | top NETWORK 激活；无师傅 → TECH MANUAL | `DispatchPolicyServiceAssignmentPostgresIT#taskCreatedActivatesTopRankedNetworkFromFrozenDispatch` |
| M337-02 | 有师傅容量 | NETWORK + TECHNICIAN 均 ACTIVE | `…#taskCreatedActivatesTechnicianUnderTopNetworkWhenCapacityExists` |
| M337-03 | 删除容量 | 无 assignment + POLICY_MANUAL | `…#emptyCapacityLeavesTaskWithoutNetworkAssignment` |
| M337-04 | 删除覆盖 | 无 assignment + POLICY_MANUAL + Inbox SUCCEEDED | `…#missingCoverageFailsClosedWithoutNetworkAssignment` |
| M337-05 | 异地高容量 vs 同城弱网 | 选同城 WEAK | `…#coveragePreferMatchingCityOverHigherCapacityOutOfRegion` |
| M337-06 | policy.scope 不匹配 | MANUAL / POLICY_SCOPE_MISMATCH | `DefaultDispatchRuntimeTest#policyScopeMismatchFailsClosed` |
| M337-07 | REGION_SCOPE 市/区命中 | 候选 regionCodes 含 city 可通过 | `DefaultDispatchRuntimeTest` + IT |
| M337-08 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- 比例分配、师傅 Coverage、Admin Coverage CRUD、OpenAPI
