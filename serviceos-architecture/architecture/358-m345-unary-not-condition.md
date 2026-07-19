---
title: M345 一元取反条件积木
status: Implemented
milestone: M345
lastUpdated: 2026-07-19
relatedMilestones: [M342, M310]
---

# M345 一元取反条件积木

## 目标

ConditionBuilder 支持 SERVICEOS_EXPR_V1 一元 `!` 的编译/解析 round-trip 与积木取反开关。

## 范围

- AST `ConditionNot`；ExprParser `parseNot`（不误吃 `!=`）
- UI：原子 `!` 复选框；条件组「取反本组」
- Node 冒烟 + Admin build

## 非目标

- De Morgan 规范化；Technician 共用执行器；后端/契约变更

## 验证

```bash
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```
