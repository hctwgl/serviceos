# Sprint-002：ServiceOS Admin 产品体验升级分析计划

状态：实施前分析，基于当前工作区与 PR #222（`dbd15607`）事实
日期：2026-07-24

## 1. 目标与范围

本批次把 Admin 的三个已存在入口升级为新能源现场履约操作平台：

```text
项目详情
  → 新能源履约项目驾驶舱
履约方案
  → Fulfillment Blueprint Designer
工单
  → 履约过程 WorkOrder Workspace
```

实现范围严格限定为 `serviceos-frontend/apps/admin`，用户明确要求的本分析文件是范围外唯一文档输出。原则上不增加后端领域对象、微服务、接口、迁移或 `vben/` 基础包改动。

风险等级：R1（跨三个 Admin 工作区的前端体验重构，复用既有 API；不改变授权、领域状态机、事务和契约）。若实现过程中发现必须改变接口、权限或业务状态，立即停止扩展并回到事实确认，不在本批次自行设计后端语义。

## 2. 已读取事实

### 产品事实源

- `serviceos-architecture/product-design/README.md`：Admin 使用 A+「经典专业任务型」视觉；项目列表是唯一项目入口；履约方案归项目详情；不设一级配置中心。
- `01-design-baseline.md`：浅色侧栏、浅蓝灰工作区、细边框、高信息密度；首屏优先展示责任、风险、下一步、状态和流程位置。
- `02-screen-inventory.md`：项目详情包含项目概览、履约方案、项目工单；方案内部包含流程、表单资料、SLA、网点、审核和版本；对象工作区不是字段表。
- `03-core-product-journeys.md`：项目配置和工单工作区分别是事实入口；工作台只做投影和深链接；同一履约方案版本整体原子发布。
- `DEC-003` / `DEC-007`：一个项目可有多套履约方案；每套方案有一个草稿、一个生效版本和历史版本；新版本不改写进行中工单。
- `DEC-001` / `DEC-005`：项目协同人员、责任网点、责任师傅、当前任务处理人必须分层展示，不能合并为“负责人”。
- A+ 视觉金标：1440×900 主视口、1280×800 响应式检查；白色约 176px 侧栏、白色约 54px 顶栏、约 276px 工作区上下文栏。

### PR #222 已有工程事实

- `ProjectWorkspacePage` 已消费 `/admin/projects/{projectId}/workspace`，可获得项目、客户、周期、区域、参与网点、使用中工单数和履约方案摘要。
- `ProjectFulfillmentPage` 已消费方案列表和方案详情；已有真实新建、匹配模拟、草稿入口。
- `ProjectFulfillmentDraftPage` + `FulfillmentStageDesigner` 已消费草稿、结构化匹配条件、阶段责任和运行绑定；保存与校验使用 `If-Match` 和幂等键。
- `ProjectFulfillmentPublishPage` 已具备校验、预览、影响分析、版本比较和原子发布入口；历史版本只读事实来自已有 API。
- `WorkOrderWorkspacePage` 已消费工单工作区摘要、`workflowStages`、允许动作、任务区块、预约上门、表单资料、审核整改、外部回传和时间线区块。
- `apps/admin/src/styles/app.css` 已有 A+ 令牌和工作区基础样式，但页面结构仍以卡片、配置表和长详情为主。
- `serviceos-frontend/packages/api-client` 的既有类型与函数是本批次唯一 API 依赖来源；不修改共享包。

## 3. Redesign 取舍

Mode：Redesign · Overhaul（保留产品与技术契约，重排 Admin 业务工作区层级）。

### 保留

- 现有 Vue 3 + Vben 应用壳、浅色侧栏、顶栏、项目范围切换和现有权限/错误/加载状态。
- 现有路由路径、深链接、真实 API、服务端 `allowedActions`、版本原子发布和责任链语义。
- `@serviceos/design-system` 作为 Ant Design Vue 唯一导出边界；不写入 `vben/`。
- 现有业务页签和工单区块接口，不删除表单资料、审核整改、外部回传和操作日志能力。

### 改善

- 项目详情由“项目概览 + 方案卡片”改成项目摘要、履约健康、阶段进度、风险和业务事件的工作区。
- 履约方案由“方案列表 + 配置模块卡片”改成左侧方案/设计导航、中间流程画布、右侧节点属性和发布状态。
- 工单由“长摘要 + 大片段页签”改成顶部履约摘要、左侧业务时间线、中间当前任务、右侧上下文；原有区块以下沉页签保留。
- 将用户可见的英文枚举、内部字段名、原始 ID 和实现细节继续收敛到展示层映射，不让页面按接口字段自动生成。
- 使用明确的 loading、empty、error、permission、read-only 和 unavailable 视觉状态，不把聚合失败显示为零。

### 移除

- 以传统表格作为履约方案首屏的主要交互。
- 把每个业务对象拆成“列表 / 详情 / 编辑 / 保存”的平行 CRUD 页面。
- 一级“配置中心”式导航；履约方案继续从项目详情进入。
- 前端自行推断权限、风险、责任和候选资格；不添加静默 mock 数据兜底。

### 最高风险改动

项目详情和方案设计器会引入新的局部信息架构与 query tab/section 状态。处理方式是保留现有路由及 API，query 只表达当前视图，不持有业务状态；写操作仍回到已有正式命令和服务端结果。

### 回退方式

每个页面保留现有查询和命令 composable；若某个新业务组件出现数据不完整或接口失败，显示明确 unavailable/error 状态，不切回旧页面、不伪造成功、不增加兼容路由。Git 可按页面文件粒度回退。

## 4. 设计读数与视觉系统

```yaml
Design Read:
  artifact: Admin 项目运营工作区、履约方案设计器、工单履约过程工作区
  audience: 平台运营、项目经理、履约配置负责人、质量与调度协作者
  visual-language: A+ 经典专业任务型 + 新能源现场运营的浅色高密度信息架构
  mode: redesign-overhaul
  constraints: 1440×900 主视口、1280×800 响应式、中文业务语言、真实服务端事实
  visual-variance: 5
  motion-intensity: 3
  information-density: 8
  asset-dependence: 2
  brand-fidelity: 9
```

四个定位问题：

- 叙事角色：三个页面都是“数据 + 决策 + 下一步”的运营工作区，不做营销 Hero。
- 查看距离：桌面端 1m laptop 视距，首屏要能快速扫出责任、风险、阶段和可执行动作。
- 视觉温度：专业、克制、带新能源绿色语义；绿色只表示履约健康/已完成，橙红只表示真实风险。
- 容量检查：1440×900 上保留稳定三栏/双栏骨架，信息密度高但不把每个字段卡片化；1280 下画布和右栏可纵向折叠。

设计 token 采用既有 `@serviceos/design-system/tokens.css`，新增业务层只补充语义映射：

- 主色：既有企业蓝，用于选中、主操作、链接和当前节点。
- 辅色：低饱和新能源绿，用于已完成、健康、已同步；不使用渐变或发光。
- 风险：既有 warning / destructive；只用于 SLA 风险、异常、阻塞和待处理。
- 容器：白底、1px 细边框、6–8px 圆角；不让每段内容都变成独立圆角卡片。
- 字体：沿用应用/设计系统字体；通过 12–23px 的层级、数字对比和分隔线建立扫描节奏。
- 动效：只保留 tab、节点选中、抽屉和状态变化的短过渡；`prefers-reduced-motion` 下关闭非必要移动。

## 5. 实现切片

### 5.1 业务组件（`apps/admin/src/components/serviceos/`）

先建立可复用的业务组件，不把所有结构塞进页面：

| 组件 | 用途 | 首批消费者 |
|---|---|---|
| `FulfillmentStageBar` | 展示完成/当前/未开始阶段和节点语义 | 项目驾驶舱、工单工作区 |
| `SlaHealthCard` | 展示 SLA 健康状态、风险数量和数据可用性 | 项目驾驶舱、工单上下文 |
| `RiskPanel` | 将超时/SLA 风险、异常、待分配和外部待处理组织成下一步入口 | 项目驾驶舱、工单上下文 |
| `WorkOrderTimeline` | 业务事件时间线，兼容空、刷新滞后和未知事件 | 项目驾驶舱、工单工作区 |
| `WorkflowCanvas` | 履约阶段节点、连接关系、节点选中态和画布级空/只读状态 | 方案设计器、草稿设计区 |
| `TaskCard` | 当前任务事实、责任和服务端允许动作 | 工单工作区 |
| `EvidenceGallery` | 资料要求/已收集数量和审核语义的紧凑展示 | 工单表单资料区 |
| `VersionBadge` | 草稿/生效/历史版本的中文展示 | 方案设计器、发布区、工单摘要 |

组件只负责业务展示和交互事件；状态、权限和写入仍由页面/既有 composable 决定。

### 5.2 页面

1. `ProjectWorkspacePage.vue`
   - 新增项目摘要头：项目状态、客户品牌、服务区域、当前方案、当前版本、履约健康。
   - 新增六个项目业务页签：项目驾驶舱、履约方案、工单执行、资源网络、质量异常、数据分析。
   - 驾驶舱使用真实项目/方案/工单投影：阶段进度、SLA 健康、风险、最近业务动态；缺失数据明确标注不可用。
   - 履约方案和工单执行页签通过正式路由进入现有方案设计器/工单中心，不在项目首页复制第二套状态。

2. `ProjectFulfillmentPage.vue` 与必要的 `ProjectFulfillmentDraftPage.vue`
   - 形成 Blueprint Designer：方案导航、基础信息/流程设计/任务模板/表单设计/证据规则/SLA/版本管理导航。
   - 流程设计用 `WorkflowCanvas` 视觉化展示开始 → 客户联系 → 预约 → 勘测 → 安装 → 验收 → 完成；节点名称优先来自真实草稿/流程摘要，缺失时展示数据不完整态。
   - 右侧属性区显示节点类型、责任方、SLA、任务、表单和证据要求；草稿编辑仍调用现有保存/校验命令。
   - `VersionBadge`、发布准备和历史版本继续遵守版本原子发布规则。

3. `WorkOrderWorkspacePage.vue`
   - 保留现有顶部动作、工作区区块 API、授权动作和 tabs。
   - 重排为顶部工单摘要、左侧业务时间线、中间当前任务、右侧客户/设备/项目/方案/异常上下文。
   - 使用 `FulfillmentStageBar`、`TaskCard`、`WorkOrderTimeline`、`RiskPanel`、`SlaHealthCard`，并保留现有表单资料、审核整改、外部回传和日志区块。

4. `menus.ts` / 路由
   - 保留现有 URL 结构和深链接；项目详情页通过 `tab`/`section` 表达视图选择。
   - 移除一级“服务履约”作为独立配置入口的产品暗示，方案入口回到“客户与项目 → 项目详情 → 履约方案”；不新增后端路由。

### 5.3 读模型适配

在 `apps/admin` 内使用纯函数 presenter/adapter 组合既有接口：

- 项目摘要：`loadAdminProjectWorkspace`。
- 项目工单投影和风险：`loadAdminWorkOrders({ projectId })`。
- 方案/草稿/版本：既有 `useProjectFulfillment*` 查询与发布查询。
- 工单过程：`loadAdminWorkOrderWorkspace`、`loadWorkspaceSection`。

适配层只转换中文标签、状态和布局所需的派生结构，不创建新的领域状态。当前接口没有项目级事件时间线或精确 SLA 百分比时，使用真实工单投影和“暂时无法获取/需要进入工单查看”说明；不伪造事件、数字或成功结果。若后续页面需要独立 mock，仅允许放在 adapter 边界并显式标注为本地开发数据，不进入正式真实数据路径。

## 6. 路由与接口保护

现有路径保持：

```text
/projects/:id
/projects/:id/fulfillment
/projects/:id/fulfillment/:profileId/draft
/projects/:id/fulfillment/:profileId/publish
/work-orders/:id
```

不改变：

- 服务端授权和 `allowedActions`；
- 项目/方案/版本/工单绑定语义；
- `If-Match`、幂等键、命令失败关闭和冲突刷新；
- 工单四类责任来源；
- 现有页签区块的 API 和数据范围。

## 7. 验证门禁

实现后按以下顺序验证：

1. `git diff --check`。
2. Admin 局部 `lint`、`typecheck`、`test:unit`、`build`。
3. 必须运行 `bash scripts/agent-verify.sh frontend`，覆盖三个 Web App、共享包、Admin 边界检查和生产构建。
4. 依据 A+ 金标启动 Admin，使用 1440×900 和 1280×800 检查项目驾驶舱、方案设计器、工单工作区；验证节点选择、tab/深链接、工单区块、错误/空/不可用态和无横向溢出。
5. 交付真实运行截图、页面/组件/路由/接口依赖和测试结果；截图只作为本次交付证据，不回写为仓库基线。

## 8. 非目标与明确不做

- 不新增后端领域对象、微服务、Controller、OpenAPI、事件 Schema、Flyway 或数据库字段。
- 不复制 workflow/task/form/evidence 领域模型；只重组现有读模型展示。
- 不修改 Vben 基础工程和共享设计系统。
- 不新增大量 CRUD 页面，不把项目/方案/工单拆成独立传统后台页面。
- 不通过前端隐藏按钮替代服务端授权，不根据空字段推断责任或允许动作。
- 不将未由接口提供的统计、事件、SLA 数字写成真实业务事实。
