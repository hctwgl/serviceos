---
title: M313 FORM/EVIDENCE/SLA 可视配置器
status: Implemented
milestone: M313
lastUpdated: 2026-07-19
relatedMilestones: [M283, M310, M312]
---

# M313 FORM/EVIDENCE/SLA 可视配置器

## 目标

为配置设计器提供 FORM / EVIDENCE / SLA 结构化可视编辑，双向同步定义 JSON；发布路径仍走服务端 Schema 校验与审批。

## 范围

- `StructuredAssetEditor.vue`
  - FORM：title/stage、section、field（key/label/dataType/binding/required）增删
  - EVIDENCE：title/stage、evidence item（key/name/mediaType/required）增删
  - SLA：policyKey、taskTypes CSV、targetDurationSeconds（固定 TASK_CREATED→TASK_COMPLETED/ELAPSED）
- 接入 `ConfigurationDesignerPage`（资产类型切换时展示）
- `npm run build`；Playwright `M313 FORM/EVIDENCE/SLA 可视配置器同步 JSON`

## 明确未实现

- FORM visibility/requiredWhen 积木内嵌；EVIDENCE qualityChecks 完整 UI；BUSINESS 日历 SLA；RULE/DISPATCH 全属性面板

## 验证

```bash
cd serviceos-admin-web && npm run build
# 可选：npx playwright test admin-configuration-designer
```
