---
title: M370 Admin 设计系统实施对齐验收
status: Accepted
milestone: M370
lastUpdated: 2026-07-20
---

# M370 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | Token 版本存在 | `SOS_TOKEN_VERSION=1.0.0`，文档同步 | token-version.ts / design-tokens.md |
| A2 | 分领域 Presenter | work-order/task/review/correction/evidence/sla/delivery/pricing 独立 | src/presentation/* |
| A3 | 禁止跨领域 tone 表 | `STATUS_TONES` 删除；statusTone 恒 neutral | statusLabels.ts + unit |
| A4 | Shadow 非成功色 | pricing presenter semantic=shadow | pricing-status.presenter.ts |
| A5 | 组件文档基线 | SemanticStatusTag 治理文档 | docs/components/* |
| A6 | 禁止色扫描 | check:tokens 通过 | scripts/check-hardcoded-colors.mjs |
| A7 | 构建 | vue-tsc + vite build 通过 | npm run build |
| A8 | 06 状态不变 | 仍为 Proposed | product/06 frontmatter |

## 明确不验收

AppShell、业务页产品化视觉金标（M371+）。
