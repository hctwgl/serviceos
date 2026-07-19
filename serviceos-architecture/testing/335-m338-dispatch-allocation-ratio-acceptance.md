---
title: M338 DISPATCH 签约比例缺口评分验收矩阵
status: Implemented
milestone: M338
lastUpdated: 2026-07-19
---

# M338 DISPATCH 签约比例缺口评分验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M338-01 | enabled + ORDER_COUNT，欠配缺口大 | 低产能欠配网点优先 | `DefaultDispatchRuntimeTest#allocationRatioGapPrefersUnderAllocatedCandidate` |
| M338-02 | allocationRatio.enabled=false | GAP 贡献 0，回退产能排序 | `…#allocationRatioDisabledZerosGapFactor` |
| M338-03 | IT：目标 WEAK 0.8 / STRONG 0.2 | ACTIVE NETWORK = WEAK | `DispatchPolicyServiceAssignmentPostgresIT#allocationRatioPreferUnderAllocatedNetworkDespiteLowerCapacity` |
| M338-04 | 无目标行 | gap=0，既有 M337 行为保持 | 既有 IT（无 target 种子） |
| M338-05 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- AMOUNT/WEIGHTED_VOLUME、qualityMayOverride、Admin CRUD、OpenAPI
