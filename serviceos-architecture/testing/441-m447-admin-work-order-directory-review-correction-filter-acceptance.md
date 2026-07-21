---
title: M447 Admin 工单目录审核/整改状态筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M447
lastUpdated: 2026-07-21
---

# M447 Admin 工单目录审核/整改状态筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | reviewCorrectionStatus=REVIEW_OPEN | 仅命中存在 OPEN ReviewCase 的工单 | `listFiltersByReviewCorrectionStatus` |
| A2 | CORRECTION_ACTIVE | 仅命中 ACTIVE CorrectionCase 工单 | 同上 |
| A3 | 非法枚举（OPEN） | IllegalArgumentException | 同上 |
| A4 | 缺 evidence.read | 空结果，不泄露 | 同上 |
| A5 | Admin | 筛选控件可见 | Playwright |

产品状态：`READY_FOR_REVIEW`。
