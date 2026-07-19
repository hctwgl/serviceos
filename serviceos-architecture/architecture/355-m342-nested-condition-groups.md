---
title: M342 嵌套条件组 round-trip
status: Implemented
milestone: M342
lastUpdated: 2026-07-19
relatedMilestones: [M310, M340, M341]
---

# M342 嵌套条件组 round-trip

## 目标

为 SERVICEOS_EXPR_V1 条件积木提供嵌套括号 AND/OR 组的编译↔解析 round-trip，
并在 ConditionBuilder 中提供递归条件组 UI。

## 范围与非目标

- 范围：
  - `serviceosExprV1Blocks.ts`：递归下降解析 `( … )` / `&&` / `||`（保留 formValues 方括号）
  - `ConditionGroupBlock.vue`：递归添加/删除条件与条件组
  - `ConditionBuilder.vue`：接入递归组编辑
  - Node 冒烟 + `npm run build`
- 明确不做：
  - 一元 `!` 积木化（仍走高级源码）
  - 后端 / Flyway / OpenAPI
  - Bundle FORM fieldKey 跨资产自动发现

## 已实现

- `(a && b) || c` 与更深嵌套 formValues 混合表达式可 round-trip
- UI 可添加嵌套条件组；无法解析时仍降级高级源码

## 明确未实现

- `!` 积木、混合优先级无括号的强制规范化 UI、Technician 共用执行器

## 验证命令

```bash
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```
