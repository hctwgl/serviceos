# Admin 组件治理目录（M370）

等价于 Storybook 基线：每个核心组件记录用途、Anatomy、Variants、States、Keyboard、Accessibility、Content、Responsive、Token、Code mapping。

状态矩阵示例覆盖：loading / empty / error / permission / stale / offline / shadow。

| 组件 | 文档 |
|---|---|
| SemanticStatusTag | [SemanticStatusTag.md](./SemanticStatusTag.md) |
| ScopeBar | [ScopeBar.md](./ScopeBar.md) |
| FreshnessIndicator | `src/patterns/FreshnessIndicator.vue` |
| PageContainer | `src/patterns/PageContainer.vue`（模板扩展见 M372） |
| DeveloperDiagnosticsDrawer | `src/patterns/DeveloperDiagnosticsDrawer.vue` |

破坏性组件变更必须同步本目录与 `docs/design-tokens.md`。
