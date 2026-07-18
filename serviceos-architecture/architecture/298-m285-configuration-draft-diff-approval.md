---
title: M285 配置草稿 Diff 与发布审批门禁
status: Implemented
milestone: M285
lastUpdated: 2026-07-18
relatedMilestones: [M282, M283, M284]
---

# M285 配置草稿 Diff 与发布审批门禁

## 已实现

1. 生命周期 `DRAFT → VALIDATED → APPROVED → PUBLISHED`；Flyway V112/V113；能力 `configuration.approve`。
2. `GET :diff` 相对基线统一文本 Diff；`POST :approve`；发布仅允许 APPROVED。
3. 编辑 APPROVED/VALIDATED 回退 DRAFT 并清空审批；Admin 设计器支持 Diff/审批。
4. OpenAPI 1.0.30；`ConfigurationDraftDiffApprovalPostgresIT` + 回归。

## 明确未实现

灰度/回滚、多级审批流、可视化语义 Diff、拖拽画布。
