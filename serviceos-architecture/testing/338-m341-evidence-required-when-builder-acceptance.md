---
title: M341 EVIDENCE requiredWhen 条件积木嵌入 验收矩阵
status: Implemented
milestone: M341
lastUpdated: 2026-07-19
---

# M341 EVIDENCE requiredWhen 条件积木嵌入 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M341-01 | EVIDENCE 项嵌入 ConditionBuilder | 可编辑 requiredWhen 源码同步 JSON | `StructuredAssetEditor.vue` |
| M341-02 | 空源码 | 清除 requiredWhen 字段 | `withOptionalExpression` |
| M341-03 | 既有积木回归 | formValues/上下文编译仍绿 | `serviceosExprV1Blocks.test.mjs` |
| M341-04 | Admin Web 构建 | vue-tsc / vite build 通过 | `npm run build` |

## 明确不验收

- Bundle FORM fieldKey 自动发现、嵌套括号、后端契约/迁移、吉利联调
