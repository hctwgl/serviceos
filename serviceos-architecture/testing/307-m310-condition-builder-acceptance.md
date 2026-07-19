---
title: M310 条件积木验收矩阵
status: Implemented
milestone: M310
lastUpdated: 2026-07-19
---

# M310 条件积木验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| C310-01 | AND 双原子编译 | `path == "v" && path != "v2"` | `serviceosExprV1Blocks.test.mjs` |
| C310-02 | 往返解析平面 AND | compile(parse(source)) == source | 同上 |
| C310-03 | 含括号源码 | tryParse 返回 null（进高级模式） | 同上 |
| C310-04 | 非法路径 | compile 抛错 | 同上 |
| C310-05 | Admin build | vue-tsc + vite build 成功 | `npm run build` |
| C310-06 | RULE 设计器积木 | 填写值后 JSON 含表达式 | Playwright `M310 条件积木可为 RULE…` |
