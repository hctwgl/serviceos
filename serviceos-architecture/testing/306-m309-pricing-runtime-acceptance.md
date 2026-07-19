---
title: M309 PRICING 运行时验收矩阵
status: Implemented
milestone: M309
lastUpdated: 2026-07-19
---

# M309 PRICING 运行时验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| P309-01 | 多行 when 命中 | 合计 180000；跳过未命中行 | `DefaultPricingRuntimeTest#matchesLinesAndSumsAmountMinor` |
| P309-02 | 无命中 | total=0；matched 空 | `DefaultPricingRuntimeTest#returnsZeroWhenNoLineMatches` |
| P309-03 | pricingKey 缺失 | RESOURCE_NOT_FOUND | `DefaultPricingRuntimeTest#failsClosedWhenPricingKeyMissing` |
| P309-04 | 冻结 Bundle PostgreSQL | total=150000 CNY | `PricingRuntimePostgresIT#calculatesFromFrozenPricingPolicy` |
