---
title: M412 Network 分配推荐解释验收矩阵
version: 0.1.0
status: Implemented
milestone: M412
lastUpdated: 2026-07-21
---

# M412 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 同城 + 已通过资质 | `recommendationTier=RECOMMENDED`，摘要以「建议优先」开头 | EvaluatorTest + PostgresIT |
| A2 | 覆盖未命中 | `CAUTION` + 原因含覆盖未命中 | PostgresIT |
| A3 | 无 ACTIVE 师傅 | `emptyReason` 解释，items 为空 | PostgresIT |
| A4 | 工作台抽屉展示推荐 | 候选卡/影响区含推荐解释，无占位文案 | Playwright workbench |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`（非 PRODUCT_ACCEPTED / VISUAL_APPROVED）。
