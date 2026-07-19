---
title: M345 一元取反条件积木 验收矩阵
status: Implemented
milestone: M345
lastUpdated: 2026-07-19
---

# M345 一元取反条件积木 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M345-01 | `!atom` round-trip | 一致 | `serviceosExprV1Blocks.test.mjs` |
| M345-02 | `!(a && b)` round-trip | 一致 | 同上 |
| M345-03 | 混合 `!formValues \|\| task` | 一致 | 同上 |
| M345-04 | `!=` 比较不受影响 | 仍可解析 | 既有平面测试 |
| M345-05 | Admin build | 通过 | `npm run build` |

## 明确不验收

- De Morgan UI、后端契约、吉利联调
