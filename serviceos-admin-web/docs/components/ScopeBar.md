# ScopeBar

## 用途

展示当前租户/项目/区域/组织范围与数据更新摘要。

## Anatomy

租户 · 项目 · 区域 · 受限范围 Tag · 数据更新 · FreshnessIndicator

## States

- 全量范围 / 受限范围
- asOf 可知 / 未知

## Accessibility

`role="status"`；区域缺口用 Tooltip 说明 UI_DATA_GAP。

## Token / Code

`src/patterns/ScopeBar.vue`
