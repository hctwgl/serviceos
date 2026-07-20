---
title: 06 设计系统 Admin 实施映射（M370）
version: 0.1.0
status: Draft
relatedSpec: 06-design-system-accessibility-spec.md
---

# 06 → Admin Web 实施映射（M370）

本文件记录 Admin Web 对 Proposed 规范 `06-design-system-accessibility-spec.md` 的落实情况。
**不改变 06 规范的 status。**

| 06 条款 | Admin 落实 | 位置 |
|---|---|---|
| Token 语义名 | 已落实 v1.0.0 | `serviceos-admin-web/src/styles/tokens.css` |
| 状态语义表 | 已落实 SemanticStatus | `presentation/semantic-status.ts` |
| 分领域不得共用颜色映射 | 已落实独立 Presenter | `presentation/*-status.presenter.ts` |
| 组件分层 | 部分（Foundations + Domain Tag） | M371+ 继续 Patterns/Pages |
| ScopeBar / Freshness | 未落实 | M371 |
| Modal/Drawer/Full page 规则 | 文档约束，页面未全迁 | M372+ |
| Storybook | 等价 docs/components | M370；可后续换 Storybook |
| 可访问性基线 | 焦点 outline + reduced-motion | global.css；旅程验证在 M377 |

## 已知差距

- 多数业务页仍待迁移模板与 Presenter 消费（M373～M376）
- 完整 a11y 人工旅程与视觉金标在 M377
