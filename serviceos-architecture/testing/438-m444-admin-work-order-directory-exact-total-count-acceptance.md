---
title: M444 Admin 工单目录精确全量 COUNT 验收矩阵
version: 0.1.0
status: Implemented
milestone: M444
lastUpdated: 2026-07-21
---

# M444 Admin 工单目录精确全量 COUNT 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 跨页总数 | totalCount 覆盖全部匹配行，与 page size 无关 | `listExposesExactTotalCountAcrossPagesBeyondFormerCap` |
| A2 | 超过原 100 封顶 | totalCount=精确值且 truncated=false | 同上 |
| A3 | Admin 工具栏 | 展示「共 N 条」 | Playwright |

产品状态：`READY_FOR_REVIEW`。
