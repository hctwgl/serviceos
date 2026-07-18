---
title: M294 RULE/DISPATCH 配置设计器
status: Implemented
milestone: M294
lastUpdated: 2026-07-18
relatedMilestones: [M282, M283]
---

# M294 RULE/DISPATCH 配置设计器

## 目标

将配置草稿设计器扩展到 `RULE`（审核规则）与 `DISPATCH`（派单策略）：Schema 门禁、校验/审批/发布、Admin 资产类型选择。

## 范围

1. 新增 `rule.schema.json` 并嵌入运行时；同步 `dispatch.schema.json`；
2. `DESIGNER_TYPES` 纳入 RULE/DISPATCH；
3. Postgres IT + OpenAPI enum + Admin 选择器。

## 明确未实现

NOTIFICATION/ASSIGNEE/INTEGRATION/PRICING 设计器、RULE 运行时执行引擎、历史回放。
