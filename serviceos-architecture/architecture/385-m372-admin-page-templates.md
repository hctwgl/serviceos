---
title: M372 Admin 标准页面模板
status: Implemented
milestone: M372
lastUpdated: 2026-07-20
relatedMilestones: [M371]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M372 Admin 标准页面模板

## 已实现

- List / Detail / Workbench / Form / Configuration / DedicatedFlow 布局
- QueryPanel、ListToolbar、StickyActionBar
- SavedViewBar 工具栏密度重构（Select + Modal 保存/分享）

## 验证

```bash
cd serviceos-admin-web && npm run build && npm run test:unit
```
