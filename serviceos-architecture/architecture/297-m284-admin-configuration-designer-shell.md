---
title: M284 Admin 配置设计器壳（结构预览 + JSON 编辑）
status: Implemented
milestone: M284
lastUpdated: 2026-07-18
relatedMilestones: [M282, M283]
---

# M284 Admin 配置设计器壳

## 已实现

1. Page Registry `ADMIN.CONFIGURATION.DESIGNER`，catalog **page-registry-v17**；
2. Admin 路由 `/configuration/designer` + `ConfigurationDesignerPage`；
3. 草稿列表/创建/保存/校验/发布；WORKFLOW 节点结构预览；
4. `PortalContextPostgresIT` 同步；Admin `vue-tsc`/Vite 构建通过；Playwright 冒烟用例。

## 明确未实现

拖拽画布、Diff/审批/灰度、可视化表单控件面板。
