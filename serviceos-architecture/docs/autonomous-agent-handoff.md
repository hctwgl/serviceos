---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#174：M321～M347 Draft stacked
- 本分支：`cursor/m348-dispatch-residual-editor-88d5` — **M348** DISPATCH 残留编辑器（Draft，base=#174）
- latestMilestone：**M348**
- Flyway：**127**；OpenAPI：**1.0.43**
- `baselineCommit`：功能提交后回填

## 本会话新增

| PR | Milestone |
|---|---|
| #174 | M347 INTEGRATION Mapping DSL UI |
| （本 PR） | M348 DISPATCH scope/fallback/allocationRatio 编辑器 |

Admin Mapping DSL（M347）与 DISPATCH 残留编辑器（M348）已交付。
栈尖：`#148 → … → #174 → M348`。

## 下一（可无阻塞推进）

1. **M349** — Technician Web FORM 条件执行器（H5 当前对 visibility/requiredWhen fail-close）

## 硬门禁（不可发明推进）

1. **AMOUNT/加权比例** — 需业务确认口径，暂停
2. **BUSINESS 日历 SLA / 结算落账** — R3，需独立批准
3. **吉利联调** — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
