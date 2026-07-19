---
title: M340 FORM ConditionBuilder 条件积木嵌入
status: Implemented
milestone: M340
lastUpdated: 2026-07-19
relatedMilestones: [M310, M315, M53]
---

# M340 FORM ConditionBuilder 条件积木嵌入

## 目标

在 Admin FORM 结构化配置器中嵌入 `ConditionBuilder`，支持 `formValues["fieldKey"]`
积木原子与布尔/数值字面量，编辑 section `visibility`、field `visibleWhen` / `requiredWhen`，
并与定义 JSON 双向同步。

## 范围与非目标

- 范围：
  - `serviceosExprV1Blocks.ts`：`formValues["…"]` 编译/解析；`valueKind` string|boolean|number
  - `ConditionBuilder.vue`：`formFieldKeys` 下拉扩展；字面量类型选择
  - `StructuredAssetEditor.vue`：FORM section/field 条件积木；空源码清除可选表达式
  - Node 冒烟测试 + `npm run build`
- 明确不做：
  - 嵌套括号混合组完整 round-trip（递延后续）
  - EVIDENCE `requiredWhen` 积木嵌入
  - 后端 / Flyway / OpenAPI 变更（运行时与 Schema 已就绪）
  - Playwright 本地 Keycloak 依赖用例（可选）

## 已实现

- FORM 可视配置器可编辑 visibility / visibleWhen / requiredWhen
- 积木生成合法 `SERVICEOS_EXPR_V1`，含 `formValues["needs-photo"] == true` 形态
- 空条件不写入 JSON，避免发布期空表达式

## 明确未实现

- 嵌套括号 round-trip / 递归条件组 UI
- EVIDENCE 条件积木、validationRules 积木编辑器
- Technician Web/iOS 条件显隐共用执行器深化

## 验证命令

```bash
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```
