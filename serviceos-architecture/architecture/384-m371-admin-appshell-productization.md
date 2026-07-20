---
title: M371 Admin AppShell 产品化
status: Implemented
milestone: M371
lastUpdated: 2026-07-20
relatedMilestones: [M370]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M371 Admin AppShell 产品化

## 目标

交付成熟运营后台壳层：顶栏、可折叠侧栏、品牌、面包屑、ScopeBar、Freshness、用户菜单、全局搜索入口、开发诊断抽屉；删除侧栏技术说明。

## 已实现

- Ant Design Layout 壳层（56 / 216↔64）
- ScopeBar + FreshnessIndicator
- DeveloperDiagnosticsDrawer（DEV）
- PageContainer 基线组件
- 服务端 Navigation / Recent / Context 选择器与既有 testid 兼容

## 非目标

标准页模板填充（M372）、业务页重写（M373+）。

## 验证

```bash
cd serviceos-admin-web && npm run build && npm run test:unit
```
