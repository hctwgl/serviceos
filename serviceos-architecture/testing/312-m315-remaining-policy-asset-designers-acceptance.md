---
title: M315 剩余策略资产设计器验收矩阵
status: Implemented
milestone: M315
lastUpdated: 2026-07-19
---

# M315 剩余策略资产设计器验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| D315-01 | RULE 条件积木 | JSON when 含 BYD_OCEAN | Playwright M310/M315 RULE |
| D315-02 | PRICING 改 amount | JSON 含 28800 | Playwright M315 PRICING/INTEGRATION |
| D315-03 | INTEGRATION 加 mapping | JSON 含 map_ | 同上 |
| D315-04 | Admin build | vue-tsc + vite 成功 | `npm run build` |
