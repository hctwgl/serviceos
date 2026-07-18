---
title: M283 FORM/EVIDENCE/SLA 配置设计器 API
status: Implemented
milestone: M283
lastUpdated: 2026-07-18
relatedMilestones: [M282]
---

# M283 FORM/EVIDENCE/SLA 配置设计器 API

## 已实现

1. 设计器允许资产类型扩展为 `WORKFLOW` | `FORM` | `EVIDENCE` | `SLA`；其余类型失败关闭。
2. 复用 M282 草稿表与能力；OpenAPI 1.0.29 枚举同步。
3. `FormEvidenceSlaDesignerPostgresIT` 覆盖三类校验发布；非法 FORM 与 RULE 拒绝。

## 明确未实现

Admin 可视化画布、Diff/审批/灰度、Bundle 组装 UI、DISPATCH/PRICING/NOTIFICATION 等类型。
