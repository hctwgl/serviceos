---
title: M342 嵌套条件组 round-trip 验收矩阵
status: Implemented
milestone: M342
lastUpdated: 2026-07-19
---

# M342 嵌套条件组 round-trip 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M342-01 | 平面 AND 编译/解析 | round-trip 一致 | `serviceosExprV1Blocks.test.mjs` |
| M342-02 | `(a && b) \|\| c` | 解析为 OR[AND, atom]；编译还原 | 同上 |
| M342-03 | 深嵌套 + formValues | round-trip 一致 | 同上 |
| M342-04 | 一元 `!` | 解析返回 null（高级源码） | 同上 |
| M342-05 | 递归条件组 UI | 可添加嵌套组 | `ConditionGroupBlock.vue` |
| M342-06 | Admin Web 构建 | vue-tsc / vite build 通过 | `npm run build` |

## 明确不验收

- `!` 积木化、后端契约/迁移、吉利联调
