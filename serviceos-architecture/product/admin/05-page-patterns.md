---
title: Admin 页面模式与交互规范
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# Admin 页面模式与交互规范

## 1. 列表页

结构：

```text
PageHeader
QueryPanel
ListToolbar
Ant Table
Pagination
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
- 批量操作明确作用于当前页还是全部查询结果。

## 2. 详情页

结构：

```text
PageHeader
SummaryHeader
BusinessProgress / RiskAlert
Tabs
StickyActionBar（适用时）
```

首屏必须回答：对象、业务编号、状态、项目、当前阶段、责任人、SLA、风险、下一步和主要动作。

详情 Tab 只按用户认知组织，例如“基本信息、履约任务、表单资料、审核整改、活动记录”，不直接按数据库表或聚合命名。

## 3. 工作台

优先级固定为：

1. 待我处理；
2. 即将超时；
3. 已超时；
4. 重大异常；
5. 最近处理。

工作台指标必须可下钻到同口径列表，并显示范围和更新时间。禁止用大量同宽 KPI 卡片替代任务队列。

## 4. 表单页

- 使用 Ant Form；
- Label 永久可见；
- 按业务分区；
- 必填、单位、限制、帮助和错误与字段关联；
- 服务端字段错误定位到具体字段，同时提供顶部错误摘要；
- 长表单使用底部固定操作区；
- 离开未保存内容前提示；
- 内部 ID 通过实体选择器选择，不允许普通文本输入。

## 5. 配置页

配置页必须区分：当前生效版本、草稿、校验结果、影响、发布记录。

基础结构：

```text
ConfigurationContext
VersionSummary
ConfigurationTabs / Editor
ValidationPanel
PublishEntry
```

高级技术配置使用渐进披露：业务模式显示中文选项和解释；高级模式才显示 key、binding、表达式和 JSON。

## 6. 专用流程页

用于终审、改派、配置发布、批量操作、事实更正和整改复审。

根据复杂度采用：左侧步骤/对象导航、中间主工作区、右侧上下文和底部固定操作栏。不得使用普通 Modal 承载多步骤、高风险和需要历史上下文的操作。

## 7. 操作容器

| 容器 | 适用场景 |
|---|---|
| Popover | 简短解释、低风险单项选择 |
| Drawer | 快速预览、单屏短操作、简单实体选择 |
| Modal | 单一步骤短确认 |
| Full Page / Dedicated Flow | 多步骤、高风险、版本/历史/影响分析 |

## 8. 实体展示和选择

统一提供 EntityName、EntityLink、ProjectPicker、OrganizationPicker、NetworkPicker、TechnicianPicker、PrincipalPicker 等组件。

规则：

- 主要显示名称；
- 次要显示业务编码；
- 完整 UUID 只在诊断或受控复制入口；
- 不使用“输入 networkId/principalId”；
- 避免逐行 N+1 查询；
- 名称缺失时显示“名称暂不可用”并登记数据缺口，不展示 UUID 代替名称。

## 9. 状态语义

生命周期、任务、审核、整改、资料、SLA、交付和试算分别使用领域 Presenter，再映射到：neutral、info、success、warning、critical、stale、offline、shadow。

状态不得只依赖颜色：成功有对勾或明确文本；警告有原因；危险有下一步；Stale 有更新时间；Shadow 明确“非正式”。

## 10. 技术信息隔离

普通页面不得直接展示 API Path、If-Match、Idempotency-Key、aggregateVersion、resourceVersion、principalIds、sourceId、raw capability、原始枚举或英文异常。

普通错误展示：发生了什么、数据是否保存、下一步、重试和问题编号。完整 correlationId 和请求上下文进入 DeveloperDiagnosticsDrawer 或系统诊断。

## 11. 页面状态

每张页面按适用范围实现：首次加载、局部刷新、空数据、网络错误、服务端错误、无权限、资源不存在、数据延迟、并发冲突、被他人处理、草稿、发布中和只读。

空状态必须说明原因和下一步，不能只写“暂无数据”。并发冲突不得自动覆盖用户输入。

## 12. 可访问性

- 键盘可达和可见焦点；
- 图标按钮有中文可访问名称和 Tooltip；
- Modal/Drawer 焦点管理正确；
- 200% 缩放主操作可访问；
- 支持 `prefers-reduced-motion`；
- SLA 倒计时不每秒向读屏播报；
- 表单错误与字段关联；
- 图表有表格或文字替代；
- 状态不只依赖颜色。

## 13. 视觉基线

正确顺序：产品设计 → 静态页面/真实数据样例 → 人工审查 → 接入接口 → 回归 → 再建立视觉金标。

截图测试只能防回退，不能证明页面已达到产品标准。