# SemanticStatusTag

## 用途

展示分领域 Presenter 输出的语义状态；禁止接收裸领域枚举并猜色。

## Anatomy

- Tag（Ant Design Vue）
- 可选图标（非颜色信号）
- 标签文本
- Shadow 固定「影子/非正式」角标
- Tooltip（description；DEV 可附 rawCode）

## Variants

按 `semantic`：neutral / info / success / warning / critical / stale / offline / shadow

## States

| 状态 | 表现 |
|---|---|
| success | 对勾图标 + 成功动词 |
| warning | 警告图标 + 原因 |
| critical | 严重图标 + 下一步说明 |
| stale | 同步图标 + 刷新提示 |
| offline | 离线图标 + 同步说明 |
| shadow | 固定影子标签，禁用成功色 |

## Keyboard

只读展示；焦点可达 Tooltip 触发器。

## Accessibility

- 状态不只依赖颜色（图标 + 文本）
- Tooltip 提供 description
- `data-semantic` 供测试与读屏辅助

## Content

标签来自 Presenter，使用业务中文。

## Responsive

行内 flex；窄屏可换行。

## Token

`--sos-color-status-*`；Ant Tag color 经 `antTagColorForSemantic`。

## Code mapping

- `src/components/business/SemanticStatusTag.vue`
- Presenters：`src/presentation/*-status.presenter.ts`
