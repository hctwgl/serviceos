---
title: M288 CANARY 百分比流量灰度
status: Implemented
milestone: M288
lastUpdated: 2026-07-18
relatedMilestones: [M286, M287]
---

# M288 CANARY 百分比流量灰度

## 已实现

1. `traffic_percent`（Flyway V116）；CANARY 默认 0，STABLE 固定 100。
2. Resolve：`preferCanary` 强制 CANARY；否则 `hash(routingKey)%100 < trafficPercent`。
3. 入站建单以 `externalOrderCode` 作为路由键；OpenAPI 1.0.32。
4. `PercentageCanaryTrafficPostgresIT`。

## 明确未实现

动态调比例 UI、多 CANARY、自动晋级指标。
