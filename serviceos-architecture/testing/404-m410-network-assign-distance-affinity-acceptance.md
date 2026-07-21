---
title: M410 Network 分配距离亲和验收矩阵
version: 0.1.0
status: Implemented
milestone: M410
lastUpdated: 2026-07-21
---

# M410 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 同城 Coverage 命中 | `distanceTier=SAME_CITY`，摘要含中文城市名 | `AssignCandidateDistanceEvaluatorTest` + PostgresIT |
| A2 | Coverage 未命中 | `OUTSIDE_COVERAGE` + 警告 | PostgresIT |
| A3 | 工单缺行政区 | `UNKNOWN`，不伪造距离 | 单元测试 |
| A4 | 工作台抽屉展示距离 | 候选卡与影响区含距离摘要，无占位文案 | Playwright workbench |
| A5 | 工作区抽屉回归 | 候选可见并可关闭 | Playwright workspace |
| A6 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`（非 PRODUCT_ACCEPTED / VISUAL_APPROVED）。
