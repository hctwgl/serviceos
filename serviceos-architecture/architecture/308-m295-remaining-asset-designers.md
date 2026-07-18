---
title: M295 NOTIFICATION/ASSIGNEE/INTEGRATION/PRICING 设计器
status: Implemented
milestone: M295
lastUpdated: 2026-07-18
relatedMilestones: [M283, M294]
---

# M295 NOTIFICATION/ASSIGNEE/INTEGRATION/PRICING 设计器

## 目标

补齐 Phase K 剩余标准配置资产类型的 Schema 门禁与草稿设计器：`NOTIFICATION`、`ASSIGNEE_POLICY`、`INTEGRATION`、`PRICING`。

## 范围

1. 四个 JSON Schema + 运行时嵌入；
2. `DESIGNER_TYPES` 全量覆盖十大资产类型（WORKFLOW 仍走语义校验）；
3. Postgres IT + OpenAPI enum + Admin 选择器。

## 明确未实现

各资产运行时执行引擎、历史回放、条件积木 UI。
