---
title: Admin 页面模式与交互规范
version: 0.2.0
status: Proposed
lastUpdated: 2026-07-20
visualProfile: 方案 A｜经典专业风
---

# Admin 页面模式与交互规范

## 1. 已批准视觉方向

Admin 统一采用 [经典专业风视觉与页面基线](12-classic-professional-visual-baseline.md)：

- 浅色企业后台；
- 白色左侧导航；
- 标准企业蓝作为主操作和选中状态；
- 中高信息密度；
- 细边框、克制阴影、6～8px 圆角；
- 页面围绕业务任务、责任、版本、风险和下一步组织；
- 不使用营销插画、夸张渐变、深色霓虹和巨型 KPI 墙。

所有页面模式必须落在这一视觉语言内。Ant Design Vue 只提供基础组件，不自动构成页面设计。

## 2. 全局页面骨架

```text
AdminAppShell
├─ LeftNavigation
├─ GlobalHeader
└─ PageCanvas
   ├─ Breadcrumb
   ├─ PageHeader + PrimaryActions
   ├─ SummaryStrip（可选）
   └─ PageWorkspace
```

规则：

- 页面水平内边距 20～24px；
- 主区块间距 16px；
- 页面标题 24px/600；
- PageHeader 同时表达页面任务和当前范围；
- 主操作放右上角或固定操作区；
- 页面不得直接以接口对象、数据库表或 JSON 为主要结构。

## 3. 列表页

结构：

```text
PageHeader
SummaryStrip（可选）
FilterBar
ListToolbar
BusinessDataTable
Pagination
ContextDrawer / DetailPanel（可选）
```

必须满足：

- 页面右上角放“新建”等主要操作；
- 常用筛选默认显示，低频筛选进入“更多筛选”；
- 创建/编辑长表单不得常驻列表首屏；
- 表格使用业务列名和实体名称，不以 UUID 为主列；
- 状态使用领域 Presenter 转换成中文语义状态；
- 行操作直接显示不超过两个，其余进入“更多”；
- 服务端分页诚实表达 total 或 cursor，不伪造总数；
- 保存视图放列表工具栏，不铺成大块表单；
- 批量操作明确作用于当前页还是全部查询结果；
- 主标识列和操作列在横向滚动时固定；
- 表格默认行高 44～48px，支持列设置和密度切换；
- 左侧分类树适用于任务模板、组织、配置资产等稳定分类，不得作为任意筛选的替代品；
- 右侧详情面板只用于快速理解和短操作，复杂编辑进入独立页面。

## 4. 详情页 / 业务工作区

结构：

```text
PageHeader + AllowedActionBar
SummaryHeader
BusinessProgress / RiskAlert
MainWorkspace + RightContextRail
Tabs
StickyActionBar（适用时）
```

首屏必须回答：

- 这是什么对象；
- 业务编号是什么；
- 当前状态和阶段是什么；
- 当前 Task 是什么；
- 谁负责；
- SLA 和风险是什么；
- 下一步是什么；
- 当前允许做什么。

详情 Tab 只按用户认知组织，例如“基本信息、任务记录、预约与上门、表单资料、审核整改、外部回传、操作日志”，不直接按数据库表或聚合命名。

右侧上下文栏用于：

- 风险与提醒；
- 责任链；
- 外部集成摘要；
- 最近关键时间线；
- 版本或影响说明。

右侧卡片不得重复主区已经清晰表达的内容。

## 5. 工作台

优先级固定为：

1. 待我处理；
2. 即将超时；
3. 已超时；
4. 重大异常；
5. 最近处理。

工作台指标必须可下钻到同口径列表，并显示范围和更新时间。禁止用大量同宽 KPI 卡片替代任务队列。

## 6. 表单页

- 使用 Ant Form；
- Label 永久可见；
- 按业务分区；
- 必填、单位、限制、帮助和错误与字段关联；
- 服务端字段错误定位到具体字段，同时提供顶部错误摘要；
- 长表单使用底部固定操作区；
- 离开未保存内容前提示；
- 内部 ID 通过实体选择器选择，不允许普通文本输入；
- 只读版本使用清晰的只读状态，不伪装成可编辑控件；
- 草稿自动保存必须显示保存中、已保存和失败状态。

## 7. 配置中心页面

配置页必须区分：

- 当前生效版本；
- 草稿版本；
- 运行快照；
- 校验结果；
- 版本差异；
- 影响范围；
- 发布记录。

基础结构：

```text
Project / Configuration Context
VersionSummary
LeftAssetNavigation
CenterEditor / Table / Canvas
RightVersionImpactPanel
ValidationPanel
PublishEntry
```

高级技术配置使用渐进披露：业务模式显示中文选项和解释；高级模式才显示 key、binding、表达式和 JSON。

普通项目配置管理员不得直接编辑 Manifest、documentJson、assetId、stageCode、taskType code 或 slaRef。

### 7.1 项目履约配置中心

采用：

```text
SummaryStrip
→ 左侧二级配置导航
→ 配置包概览
→ 工单类型配置表
→ 右侧影响分析与版本时间线
```

必须能在一个页面理解项目、已发布版本、草稿、工单类型、配置资产、差异和影响。

### 7.2 工作流设计器

采用三栏：

```text
流程目录与节点组件
→ 可视化流程画布
→ 节点属性面板
```

画布底部展示可达性、引用、SLA 和异常路径校验结果。复杂 Workflow 不得通过通用表单或 JSON 编辑器配置。

### 7.3 任务模板中心

采用：

```text
分类树
→ 任务模板表格
→ 模板详情面板
```

详情必须表达执行角色、分配策略、表单、资料、SLA、动作、升级和 Workflow 引用关系。

## 8. 专用流程页

用于终审、改派、配置发布、批量操作、事实更正和整改复审。

根据复杂度采用：左侧步骤/对象导航、中间主工作区、右侧上下文和底部固定操作栏。不得使用普通 Modal 承载多步骤、高风险和需要历史上下文的操作。

## 9. 操作容器

| 容器 | 适用场景 |
|---|---|
| Popover | 简短解释、低风险单项选择 |
| Drawer | 快速预览、单屏短操作、简单实体选择 |
| Modal | 单一步骤短确认 |
| Full Page / Dedicated Flow | 多步骤、高风险、版本/历史/影响分析 |

Workflow 编辑、配置发布、复杂任务模板编辑、审核工作区和批量改派必须使用 Full Page 或 Dedicated Flow。

## 10. 实体展示和选择

统一提供 EntityName、EntityLink、ProjectPicker、OrganizationPicker、NetworkPicker、TechnicianPicker、PrincipalPicker、WorkflowPicker、TaskTemplatePicker、FormTemplatePicker 等组件。

规则：

- 主要显示名称；
- 次要显示业务编码；
- 完整 UUID 只在诊断或受控复制入口；
- 不使用“输入 networkId/principalId”；
- 避免逐行 N+1 查询；
- 名称缺失时显示“名称暂不可用”并登记数据缺口，不展示 UUID 代替名称。

## 11. 状态语义

生命周期、任务、审核、整改、资料、SLA、交付和试算分别使用领域 Presenter，再映射到：neutral、info、success、warning、critical、stale、offline、shadow。

状态不得只依赖颜色：成功有对勾或明确文本；警告有原因；危险有下一步；Stale 有更新时间；Shadow 明确“非正式”。

草稿、已发布、发布中、已停用和运行快照属于配置版本状态，不能与业务执行状态共用一个状态标签组件配置。

## 12. 技术信息隔离

普通页面不得直接展示 API Path、If-Match、Idempotency-Key、aggregateVersion、resourceVersion、principalIds、sourceId、raw capability、原始枚举或英文异常。

普通错误展示：发生了什么、数据是否保存、下一步、重试和问题编号。完整 correlationId 和请求上下文进入 DeveloperDiagnosticsDrawer 或系统诊断。

Workflow 画布允许在高级信息中查看节点编码，但业务名称始终为主；Task 模板编码是次级可复制信息，不得替代模板名称。

## 13. 页面状态

每张页面按适用范围实现：首次加载、局部刷新、空数据、网络错误、服务端错误、无权限、资源不存在、数据延迟、并发冲突、被他人处理、草稿、发布中和只读。

配置页面还必须实现：

- 无草稿；
- 草稿未保存；
- 校验失败；
- 版本冲突；
- 引用对象已停用；
- 发布影响计算中；
- 发布失败；
- 只读历史版本。

空状态必须说明原因和下一步，不能只写“暂无数据”。并发冲突不得自动覆盖用户输入。

## 14. 响应式

### 1440×1024

作为关键页面标准评审尺寸，必须同时显示主导航、主工作区和右侧上下文。

### 1280px

- 允许收起左侧导航；
- 右侧详情面板可切换为 Drawer；
- 表格固定主标识和操作列；
- Workflow 属性面板不得遮挡画布；
- 主操作保持可见。

### 小于 1024px

复杂配置和工作流设计提示使用桌面端，仅保证只读或少量低风险操作。

## 15. 可访问性

- 键盘可达和可见焦点；
- 图标按钮有中文可访问名称和 Tooltip；
- Modal/Drawer 焦点管理正确；
- 200% 缩放主操作可访问；
- 支持 `prefers-reduced-motion`；
- SLA 倒计时不每秒向读屏播报；
- 表单错误与字段关联；
- 图表有表格或文字替代；
- 状态不只依赖颜色；
- Workflow 节点可通过键盘选择、移动和读取属性，或提供等价的节点列表编辑方式。

## 16. 视觉基线

正确顺序：

```text
批准的产品结构和经典专业风
→ 静态页面/真实数据样例
→ 人工审查
→ 接入真实接口
→ 功能与可访问性修复
→ 产品负责人批准
→ 建立视觉金标
```

截图测试只能防回退，不能证明页面已达到产品标准。概念图确定方向，不是像素级金标；实际页面仍需逐张验收。
