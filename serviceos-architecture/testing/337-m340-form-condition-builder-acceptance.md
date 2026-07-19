---
title: M340 FORM ConditionBuilder 条件积木嵌入 验收矩阵
status: Implemented
milestone: M340
lastUpdated: 2026-07-19
---

# M340 FORM ConditionBuilder 条件积木嵌入 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M340-01 | 上下文路径 AND 编译/解析 | round-trip 一致 | `serviceosExprV1Blocks.test.mjs` |
| M340-02 | formValues + 布尔/数值字面量 | 编译为无引号 true/1800；可解析 | 同上 |
| M340-03 | 混合上下文与 formValues OR | 平面 OR 可解析 | 同上 |
| M340-04 | 非法布尔字面量 | 编译失败关闭 | 同上 |
| M340-05 | FORM 结构化编辑器嵌入积木 | section visibility / field visibleWhen/requiredWhen | `StructuredAssetEditor.vue` + `ConditionBuilder.vue` |
| M340-06 | Admin Web 类型检查构建 | vue-tsc / vite build 通过 | `npm run build` |

## 明确不验收

- 嵌套括号 round-trip、EVIDENCE requiredWhen 积木、后端契约/迁移、吉利联调
