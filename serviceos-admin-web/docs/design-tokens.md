# ServiceOS Admin Design Tokens

- **版本**：`1.0.0`（见 `src/app/token-version.ts`）
- **事实源**：`src/styles/tokens.css`
- **Ant 映射**：`src/app/app-theme.ts` → `ConfigProvider`
- **上位规范**：`serviceos-architecture/product/06-design-system-accessibility-spec.md`（Proposed；本文件为实施映射，不改变规范状态）

## 语义分组

| 分组 | CSS 变量前缀 | 用途 |
|---|---|---|
| Brand | `--sos-primary-*` / `--sos-accent-*` | 品牌识别，不得表示成功/危险 |
| Semantic | `--sos-color-status-*` | neutral/info/success/warning/critical/stale/offline/shadow |
| Surface | `--sos-color-surface-*` | 页面/卡片/悬停 |
| Text / Border / Action | `--sos-color-text-*` 等 | 文本与操作 |
| Typography / Spacing / Radius | `--sos-font-*` / `--sos-space-*` / `--sos-radius-*` | 密度与排版 |
| Elevation / Motion | `--sos-elevation-*` / `--sos-motion-*` | 层级与动效 |
| Layout | `--sos-header-height` 等 | 壳层尺寸 |
| Breakpoint / Safe / Z / Chart | 对应前缀 | 响应式、安全区、层级、图表 |

## 禁止

- 业务组件内硬编码 `red-500` / `#ff0000` 表达驳回
- 用品牌蓝表示成功
- 新增业务状态时随意新增颜色（先映射现有 semantic）

## 破坏性变更

| 版本 | 变更 | 迁移 |
|---|---|---|
| 1.0.0 | 初版语义 Token 与 Ant 映射 | 新建 |

扫描：`npm run check:tokens`
