---
title: M282 Workflow 配置设计器 API（草稿→校验→发布）
status: Implemented
milestone: M282
lastUpdated: 2026-07-18
relatedMilestones: [M268, M281]
---

# M282 Workflow 配置设计器 API

## 已实现

1. `cfg_configuration_asset_draft`（Flyway V110）与能力 `configuration.draft.write` / `configuration.publish`（V111）。
2. 草稿生命周期：create / update / get / list / `:validate` / `:publish`；仅 WORKFLOW。
3. 校验复用 `ConfigurationAssetSchemaValidator`；失败写入 `validationErrors` 且保持 DRAFT（不回滚）。
4. 发布调用既有不可变 `publishAsset`；OpenAPI 1.0.28；`WorkflowConfigurationDesignerPostgresIT` + ArchitectureTest。

## 明确未实现

可视化画布、Admin 页面、Diff/审批/灰度、FORM/EVIDENCE/SLA 设计器、Bundle 组装 UI。
