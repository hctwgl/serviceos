---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#173：M321～M346 Draft stacked
- 本分支：`cursor/m347-integration-mapping-dsl-ui-88d5` — **M347** Admin INTEGRATION Mapping DSL UI（Draft，base=#173）
- latestMilestone：**M347**
- Flyway：**127**；OpenAPI：**1.0.43**
- `baselineCommit`：功能提交后回填

## 本会话新增

| PR | Milestone |
|---|---|
| #171 | M344 EVIDENCE FORM fieldKey 发现 |
| #172 | M345 一元取反 `!` |
| #173 | M346 qualityChecks 可视编辑 |
| （本 PR） | M347 INTEGRATION Mapping DSL UI |

低代码 ConditionBuilder / FORM / EVIDENCE 深化主线（M340～M346）已收口；Admin Mapping DSL UI（M347）已交付。
栈尖：`#148 → … → #173 → M347`。

## 下一（可无阻塞推进）

1. **M348** — DISPATCH 残留编辑器：`scope`、`fallback`、`allocationRatio`（锁定 ORDER_COUNT + MONTH；不暴露 AMOUNT/WEIGHTED）
2. **M349** — Technician Web FORM 条件执行器（H5 当前对 visibility/requiredWhen fail-close）

## 硬门禁（不可发明推进）

1. **AMOUNT/加权比例** — 需业务确认口径，暂停
2. **BUSINESS 日历 SLA / 结算落账** — R3，需独立批准
3. **吉利联调** — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
