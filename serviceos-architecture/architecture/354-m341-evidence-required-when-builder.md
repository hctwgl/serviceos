---
title: M341 EVIDENCE requiredWhen 条件积木嵌入
status: Implemented
milestone: M341
lastUpdated: 2026-07-19
relatedMilestones: [M310, M340, M52]
---

# M341 EVIDENCE requiredWhen 条件积木嵌入

## 目标

在 Admin EVIDENCE 结构化配置器中嵌入 `ConditionBuilder`，编辑资料项可选
`requiredWhen`（SERVICEOS_EXPR_V1），并与定义 JSON 双向同步。

## 范围与非目标

- 范围：
  - `StructuredAssetEditor.vue` EVIDENCE 项嵌入积木；空源码清除可选表达式
  - 上下文白名单路径积木编辑（`workOrder.*` / `region.*` / `task.*`）
  - Node 冒烟回归 + `npm run build`
- 明确不做：
  - Bundle 同 stage FORM `formFieldKeys` 自动发现（高级源码仍可写 formValues）
  - 嵌套括号 round-trip（见后续 M342）
  - 后端 / Flyway / OpenAPI（Schema 与运行时 M52 已就绪）

## 已实现

- 每条 evidence item 可编辑 `requiredWhen`；复用 M340 可选表达式写入语义

## 明确未实现

- formValues 字段键自动发现、嵌套表达式 UI、qualityChecks 可视化

## 验证命令

```bash
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```
