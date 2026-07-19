---
title: M327 PRICING CalculationSnapshot 验收矩阵
status: Implemented
milestone: M327
lastUpdated: 2026-07-19
---

# M327 PRICING CalculationSnapshot 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M327-01 | workorder.fulfilled + 冻结 PRICING | Snapshot SHADOW；total/currency 正确 | `PricingCalculationSnapshotPostgresIT` |
| M327-02 | 履约事实 | `cfg_fulfillment_fact` 含 brand/client/product | 同上 |
| M327-03 | Inbox 幂等 | consumer SUCCEEDED；重复 drain 不增行 | 同上 |
| M327-04 | 审计 | `PRICING_CALCULATION_SNAPSHOT_CAPTURED` | 同上 |
| M327-05 | 模块边界 | ArchitectureTest | ArchitectureTest |
| M327-06 | 既有 Runtime | PricingRuntime 行为不变 | `DefaultPricingRuntimeTest` |

## 明确不验收

- 落账、对账、Admin、OpenAPI、pricingRef 冻结
