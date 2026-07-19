---
title: M348 DISPATCH 残留结构化编辑器 验收矩阵
status: Implemented
milestone: M348
lastUpdated: 2026-07-19
---

# M348 DISPATCH 残留结构化编辑器 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M348-01 | 编辑 scope 三码列表 | JSON 写入非空数组 | `setScopeCodes` + `dispatch-scope-*` |
| M348-02 | 编辑 fallback | onNoCandidate/manualRole/resolutionHours 同步 | `setFallbackField` |
| M348-03 | 启用 allocationRatio | enabled=true 且 period=MONTH measure=ORDER_COUNT | `setAllocationRatioEnabled` |
| M348-04 | UI 无 AMOUNT/WEIGHTED | 不提供 measure 下拉 | `dispatch-allocation-locked` |
| M348-05 | 硬过滤 order + filterKey 枚举 | schema 合法键与唯一 order 可编辑 | `filter-key` / `filter-order` |
| M348-06 | 评分 factorKey + expression | 枚举 + ConditionBuilder | `score-factor-key` |
| M348-07 | Admin build | 通过 | `npm run build` |

## 明确不验收

- AMOUNT/加权运行时、Coverage CRUD、Technician 执行器、吉利联调
