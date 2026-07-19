---
title: M315 剩余策略资产结构化设计器
status: Implemented
milestone: M315
lastUpdated: 2026-07-19
relatedMilestones: [M294, M295, M310, M313]
---

# M315 剩余策略资产结构化设计器

## 目标

补齐 P3：为 RULE / DISPATCH / ASSIGNEE_POLICY / NOTIFICATION / INTEGRATION / PRICING 提供结构化可视编辑，条目级条件积木，双向同步定义 JSON。

## 范围

- `PolicyAssetEditor.vue`
  - RULE：ruleKey/defaultAction/rules[] + when 积木
  - NOTIFICATION：defaultChannel/triggers[] + when 积木
  - ASSIGNEE_POLICY：strategies[] + when 积木
  - DISPATCH：hardFilters[] / scoring[]
  - INTEGRATION：connectorCode/direction/fieldMappings[]
  - PRICING：currency/lines[] + when 积木
- 接入 ConfigurationDesignerPage；`npm run build`；Playwright 覆盖 RULE/PRICING/INTEGRATION

## 明确未实现

- DISPATCH scope 可视化地图；INTEGRATION 出站错误码表 UI；BUSINESS 日历 SLA

## 验证

```bash
cd serviceos-admin-web && npm run build
```
