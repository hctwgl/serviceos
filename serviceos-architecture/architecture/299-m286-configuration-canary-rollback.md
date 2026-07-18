---
title: M286 配置 Bundle 灰度（CANARY）与回滚
status: Implemented
milestone: M286
lastUpdated: 2026-07-18
relatedMilestones: [M16, M285]
---

# M286 配置 Bundle 灰度与回滚

## 已实现

1. `cfg_bundle_channel_activation`（Flyway V114）与能力 `configuration.release.manage`（V115）。
2. API：activate / promote / rollback / list；OpenAPI 1.0.31。
3. `resolve(preferCanary)`：CANARY → STABLE → 兼容扫描；通道激活为项目级指针。
4. `BundleChannelCanaryRollbackPostgresIT`。

## 明确未实现

百分比流量灰度、自动指标晋级、拖拽画布。
