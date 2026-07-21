---
title: M386 Admin 工作流设计器产品化
status: Implemented
milestone: M386
lastUpdated: 2026-07-20
relatedMilestones: [M284, M385, M388]
openapiVersion: "1.0.63"
flywayVersion: "138"
---

# M386 Admin 工作流设计器产品化

## 已实现

1. 产品页 `/configuration/workflows`（`ADMIN.WORKFLOW.DESIGNER`）；
2. Page Registry v19：配置中心「工作流设计器」；
3. 三栏经典专业风：流程目录 + 节点说明 / WorkflowCanvas / 节点属性；
4. 真实 `ConfigurationDraft` WORKFLOW API：列表、新建、保存、校验、审批、发布；
5. 产品页不展示 definition JSON；JSON 仅诊断抽屉；
6. 履约配置中心「工作流」导航跳转本页；
7. Playwright + 1440 截图。

## UI_DATA_GAP

- 工作流结构化 Draft DTO 尚未独立于 `definitionJson`；画布内部仍 JSON 适配，产品页诚实标注。

## 明确未实现

- 完整拖拽组件库与自动排版/版本对比产品化；
- Task 模板实体选择器（M387）；
- 产品负责人视觉批准（`READY_FOR_REVIEW`）。
