---
title: M370 Admin 设计系统实施对齐
status: Implemented
milestone: M370
lastUpdated: 2026-07-20
relatedMilestones: [M352, M360]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M370 Admin 设计系统实施对齐

## 目标

落实 `product/06-design-system-accessibility-spec.md`（Proposed）在 Admin Web 的实施映射：
语义 Token、分领域状态 Presenter、状态组件非颜色信号、组件文档基线。
**不**将 06 规范状态改为 Accepted。

## 范围

- `serviceos-admin-web` Token / ConfigProvider 映射 / Token 版本
- `src/presentation/*` 分领域 Presenter
- `SemanticStatusTag` 与 StatusBadge/WorkOrderStatusTag/SlaCountdown 改造
- 组件文档目录与禁止硬编码色扫描
- 用户问题编号 `ERR-YYYYMMDD-XXXX`

## 非目标

- AppShell / 页面模板 / 业务页产品化（M371+）
- 后端、OpenAPI、Flyway、capability 语义
- Storybook 完整工程（本切片使用 docs/components 等价基线）

## 已实现

- `src/styles/tokens.css` v1.0.0
- `src/presentation/**`
- `docs/design-tokens.md`、`docs/components/*`
- `npm run check:tokens`
- 单元测试 `tests/unit/presentation-status.test.mjs`

## 明确未实现

完整 AppShell、标准页模板、工单/项目产品化页面（后续里程碑）。

## 验证

```bash
cd serviceos-admin-web && npm run check:tokens && npm run test:unit && npm run build
```
