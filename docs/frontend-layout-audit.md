# ServiceOS Admin 前端布局基础审计

## 1. 审计结论

本次 Sprint 只处理 `serviceos-frontend/apps/admin` 的 App Shell 与页面布局基础，不新增业务页面、后端接口、领域对象或 API。当前最主要的问题不是单个页面的视觉细节，而是布局系统同时存在多套“谁负责宽度、谁负责高度、谁负责滚动”的约定，导致浏览器尺寸变化、侧栏折叠、页面内容变长时出现跳动、裁切和横向溢出。

优先级判断：**R2，前端跨页面基础设施变更**。本次变更不触及后端、契约、租户、安全、状态机或数据库，但会影响 Admin 全部路由，因此必须做构建、类型、单元测试和真实浏览器尺寸验收。

## 2. Design Read

| 项目 | 结论 |
| --- | --- |
| 产物 | ServiceOS Admin App Shell + UI Foundation |
| 主要用户 | 项目经理、履约运营、工单主管、配置管理员 |
| 视觉语言 | 克制、稳定、高信息密度的企业运营 SaaS；新能源绿色只用于品牌和状态，不用大面积装饰 |
| 变更模式 | Redesign · Preserve / Foundation repair |
| 品牌保真 | 9/10：保留 ServiceOS 品牌色、中文信息架构、Vben/Ant Design 组件和既有业务路由 |
| 信息密度 | 7/10：固定壳层尺寸，页面内容使用自然高度；表格保持紧凑但不压缩可读性 |
| 视觉变化 | 2/3：主要清理尺寸、字体、颜色和滚动契约，不继续扩展业务视觉 |
| 动效 | 1/2：只保留稳定的侧栏宽度过渡；不使用会改变页面高度的装饰动效 |
| 资源 | 1/5：无需新增图片或外部字体，使用系统字体栈 |

### 保留、改善、移除与保护边界

- 保留：Vben `BasicLayout`、现有 Vue Router 路由树、Ant Design Vue 组件、现有 API 和 PR223 已有业务页面。
- 改善：Admin 自有 Shell 契约、尺寸 Token、滚动所有权、字体层级、表格密度和 Project Detail / Project Fulfillment 的可伸展布局。
- 移除：Admin 自有样式与 Vben 偏好之间的重复尺寸来源、业务根节点依赖 `100vh` 的固定高度、蓝图主布局上的裁切型 `overflow: hidden`。
- 保护：不修改 `serviceos-frontend/vben`，不修改 `packages/design-system` 公共包，不修改后端和契约，不改变已有 route name、path、参数和 API 调用。

## 3. 当前架构地图

```text
main.ts
  ├─ @vben/styles / @vben/styles/antd
  ├─ apps/admin/src/design-system/tokens.css (imports shared baseline)
  ├─ styles/app.css
  ├─ initPreferences(overridesPreferences)
  └─ Router + App

App.vue
  └─ Ant Design ConfigProvider
       └─ RouterView
            └─ ServiceOSBasicLayout.vue
                 └─ @vben/layouts BasicLayout
                      ├─ VbenAdminLayout
                      │    ├─ fixed header
                      │    ├─ fixed sidebar + VbenScrollbar
                      │    └─ flex content + LayoutContent
                      └─ LayoutContent -> RouterView / KeepAlive
```

### 3.1 Layout / Sidebar / Header

- `ServiceOSBasicLayout.vue` 只提供 Vben 的 logo、header left/right、user dropdown 插槽，壳层本身由 Vben `BasicLayout` 和 `VbenAdminLayout` 负责。
- Vben 通过 `preferences.header.height`、`preferences.sidebar.width`、`preferences.sidebar.collapseWidth` 计算 fixed header、fixed sidebar 和主区宽度。
- 审计基线偏好值为：header `50px`、sidebar `224px`、collapse `60px`，并关闭了 Vben 的 sidebar toggle、拖动和 hover 展开；实现阶段仅开启稳定的 header sidebar toggle，拖动和 hover 展开仍保持关闭。
- Vben 侧栏自身拥有 `VbenScrollbar`，折叠时通过 `flex-basis / min-width / max-width / width` 改变占位；它不应再由业务 CSS 重新计算一个 sidebar 宽度。
- Vben header 是固定定位，主内容通过 `margin-top: headerHeight` 避让。header 尺寸变化会同步 `--vben-header-height`，但业务页面目前仍使用另一套 ServiceOS header Token。

### 3.2 Content Container / Scrollbar

- Vben `VbenAdminLayout` 的主内容父级是 `flex: 1; flex-direction: column; overflow: hidden`，内容 `<main>` 本身是 `relative flex: 1`，业务页面需要自然撑开并由文档滚动承担长内容。
- Admin 的 `app.css` 设置了 `body { overflow: hidden; }`，但没有明确、稳定地声明哪个元素是长页面唯一滚动容器；这会把页面滚动交给 Vben 内部实现与业务根节点的 `min-height` 假设，造成内容变长时被裁切。
- 表格和蓝图画布内部的横向滚动应保留在明确的局部容器内；Shell 不应出现横向滚动。

### 3.3 Router View / KeepAlive

- 根路由使用 `ServiceOSBasicLayout.vue`，业务路由全部是其 children；没有新增页面级 layout，也没有复制业务路由。
- Vben `LayoutContent` 包装 RouterView；其 KeepAlive 条件是 `preferences.tabbar.enable && preferences.tabbar.keepAlive`。
- Admin 当前 `tabbar.enable: false`，因此 KeepAlive 实际关闭。该行为应记录为当前产品选择，不通过打开 tabbar 来掩盖布局问题；本 Sprint 不改变路由缓存产品策略。

### 3.4 ResizeObserver

- Vben `useLayoutContentStyle()` 使用 `ResizeObserver` 观察 LayoutContent，写入 `--vben-content-height` 和 `--vben-content-width`，用于 overlay 位置计算。
- Vben `VbenAdminLayout` 同时根据 header wrapper 高度写入 `--vben-header-height`。
- Admin 没有第二个应用级 ResizeObserver；问题来自业务 CSS 与 Vben 已计算尺寸不一致，不应再添加一个会争夺高度所有权的观察器。

### 3.5 CSS Variables / Theme Token

当前存在三组来源：

1. `packages/design-system/src/tokens.css` 的 `--sos-*` 颜色、字号、radius、sidebar/header 尺寸；
2. Vben/Shadcn 的 `--background`、`--foreground`、`--primary`、`--border`、`--muted-*` 等 HSL Token；
3. `apps/admin/src/styles/app.css` 中大量业务样式直接混用两套变量，并在文件内继续修改布局规则。

其中最危险的是尺寸漂移：共享 Token 当前是 sidebar `176px`、header `54px`，Vben 偏好却是 sidebar `224px`、header `50px`，并且 `app.css` 在 `max-width: 1280px` 又将 sidebar 改为 `164px`。页面根节点还使用 `calc(100vh - var(--sos-header-height))`，因此浏览器尺寸和折叠状态下容易出现空白、覆盖或裁切。

## 4. 具体问题清单

| 优先级 | 问题 | 证据 / 影响 | 修复方向 |
| --- | --- | --- | --- |
| P0 | 滚动所有权不清晰 | `body { overflow: hidden }`；Vben content 父级也有 `overflow: hidden`；业务根节点只设置 `min-height`，长内容没有统一滚动契约 | 由 App 文档负责垂直滚动；Shell 只锁定横向溢出；页面根节点使用自然高度和 `min-height: 100%` |
| P0 | header/sidebar 尺寸漂移 | Vben 偏好为 `50/224/60`，共享 ServiceOS Token 为 `54/176/60`，媒体查询又写 `164px` | 建立 Admin 本地 Foundation Token；header/sidebar 尺寸只由 preferences + Vben 负责，业务 CSS 不再自行减 `100vh` |
| P0 | 蓝图工作区会裁切 | `.fulfillment-blueprint-layout` 使用 `min-height: 635px; overflow: hidden`；1366×768 下扣除 header 和页面上下文后必然超过可视区域 | 去掉布局级固定高度和裁切；中间流程画布只在自身拥有横向滚动 |
| P1 | 页面根节点重复计算 viewport 高度 | Project Detail、WorkOrder、Fulfillment 等根节点使用 `min-height: calc(100vh - var(--sos-header-height))`，但 Vben 已用实际 header wrapper margin | 改为 `min-height: 100%` / `min-block-size`，由内容自然撑开；保留 padding，不再二次减 header |
| P1 | 横向溢出没有单一边界 | 多个 grid/flex 子项缺少 `min-width: 0`，header 搜索和项目切换器有固定最小宽度；页面整体没有 `overflow-x: clip` 防线 | 所有 Shell flex/grid 关键子项补 `min-width: 0`；仅表格、画布、tab strip 允许局部横滚 |
| P1 | 字体和颜色来源分裂 | `app.css` 同时使用 `--sos-*` 和 Vben HSL 变量，业务页面仍有直接色值 / font-size；未定义变量会退回浏览器默认 | Admin 新增 `src/design-system/{tokens,layout,typography,components}.css`，由一个入口加载，提供语义别名和基础组件密度 |
| P1 | app.css 负责过多职责 | 450+ 行单文件同时包含 Shell、表格、Project Detail、Fulfillment、WorkOrder 和旧业务样式；重复媒体查询放大回归风险 | 只新增基础层并在 app.css 末端建立兼容收口；本 Sprint 不做业务重写，不扩大 diff |
| P2 | 页面级 `overflow: hidden` 容易误伤 | summary/card 的裁切规则与布局级裁切混在一起，长标题、描述、抽屉内容被截断风险高 | 只保留对明确单行字段的 ellipsis；布局容器改为 visible/auto；可视验收检查文字裁切 |
| P2 | 自动隐藏/拖动配置没有统一预期 | 当前 header 为 `fixed`，不是 auto；sidebar draggable/expand-on-hover 关闭；Vben 内部仍存在相关路径，误用业务 CSS 会产生跳动 | 保持自动隐藏、拖动和 hover 展开关闭；提供稳定的 header toggle 与 sidebar collapse，验证折叠按钮、宽度占位、内容不覆盖 |

## 5. Foundation 实施决策

1. 在 `apps/admin/src/design-system` 建立四个文件，作为 Admin 自有基础层：
   - `tokens.css`：颜色、状态、尺寸、间距、圆角、阴影、z-index 和字体栈；
   - `layout.css`：html/body/#app、Shell 主区、页面根节点、滚动和响应式约束；
   - `typography.css`：正文、标题、辅助信息、表格层级和可访问焦点；
   - `components.css`：Ant 表格/卡片/输入控件的基础密度，以及业务容器的非裁切默认值。
2. `main.ts` 只加载 Admin Foundation 入口和既有业务样式；不改 Vben 源码和公共 design-system 包。
3. Vben preferences 是 Shell 尺寸的唯一事实源。Foundation Token 只通过别名与其对齐，不在页面中重新减 header 或猜 sidebar 宽度。
4. 页面根节点默认 `width: 100%; min-width: 0; min-height: 100%;`，垂直内容自然增长；整体横向溢出在 `#app` 防止，业务横滚只发生在明确的表格/画布容器。
5. Project Detail 与 Project Fulfillment 仅进行布局基础修复：移除固定 viewport 高度和蓝图布局裁切，补齐 grid/flex 的 `min-width: 0`，不增加业务数据、页面和 API。

## 6. 验收矩阵

| 尺寸 | Shell | 侧栏展开 | 侧栏收起 | Project Detail | Project Fulfillment | 长页面滚动 |
| --- | --- | --- | --- | --- | --- | --- |
| 1366×768 | header 50px、内容不被覆盖 | 通过 | 通过 | 无裁切、可纵向滚动 | 三列按可用宽度收缩，内容不被固定高度截断 | 通过 |
| 1440×900 | header 50px、主区无横滚 | 通过 | 通过 | 信息层级稳定 | 画布横滚局限在画布，外层不横滚 | 通过 |
| 1920×1080 | header/sidebar 占位稳定 | 通过 | 通过 | 内容自然撑开 | 三列完整显示 | 通过 |

每个尺寸至少检查：首屏加载与 console error、整体 scrollWidth、侧栏开关两态、header 高度、页面底部可达、长标题/描述、Project Detail、Project Fulfillment，以及画布/表格的局部横向滚动。

## 7. 非目标与回滚

- 非目标：业务流程、字段、API、后端模块、状态机、权限策略、PR223 产品方向和新增业务组件。
- 回滚边界：本 Sprint 只涉及 `apps/admin` 的入口、偏好、Foundation CSS 和两张已有页面的布局样式；若浏览器验收失败，可整体回滚这些文件，不影响后端和其他前端应用。
