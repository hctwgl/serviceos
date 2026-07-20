---
title: ServiceOS 设计系统与可访问性规格
version: 0.2.0
status: Proposed
lastUpdated: 2026-07-20
---

# ServiceOS 设计系统与可访问性规格

## 1. 目标

建立支持高密度运营后台、网点协作和现场移动端的统一设计语言。设计系统统一语义和基础组件，不强迫不同 Portal 使用同一页面布局。

## 2. Portal 视觉配置

### 2.1 Admin

产品负责人已经批准 Admin 使用 **方案 A｜经典专业风**：

- 浅色画布和白色导航；
- 标准企业蓝主色；
- 中高信息密度；
- 清晰边框、克制阴影和 6～8px 圆角；
- 企业级表格、详情工作区和复杂配置三栏布局；
- 不使用营销插画、夸张渐变、霓虹、玻璃拟态或大面积深色背景。

详细页面和视觉事实源：`product/admin/12-classic-professional-visual-baseline.md`。

该配置只影响 Admin 的视觉和桌面布局，不改变跨 Portal 状态语义、可访问性、安全和组件行为。

### 2.2 Network 与 Technician

Network 和 Technician 共享品牌 Token、状态语义、图标、文案和基础组件，但根据设备、网络条件、触控和任务密度使用独立布局。不得直接缩放或复制 Admin 高密度页面。

## 3. Token

### 3.1 基础

- color：surface/text/border/action/semantic；
- typography：正文、辅助、表格、数字、标题；
- spacing：4 的倍数体系；
- radius/elevation；
- motion duration/easing；
- desktop density 与 mobile touch target；
- breakpoint 和 safe area；
- z-index 层级；
- chart palette。

Token 使用语义名，例如 `color-status-critical-bg`，禁止页面写 `red-500` 表示业务含义。品牌换肤不得改变危险/成功等系统语义。

Admin 推荐语义值由经典专业风基线给出，代码中只能通过 Token 消费，不允许页面散落任意品牌色、圆角和阴影值。

## 4. 状态语义

| 语义 | 用途 | 必需非颜色信号 |
|---|---|---|
| Neutral | 未开始/普通信息 | 文本标签 |
| Info | 处理中/提示 | 图标 + 文本 |
| Success | 已由服务器确认成功 | 对勾 + 明确动词 |
| Warning | 风险/需注意但可继续 | 警告图标 + 原因 |
| Critical | 阻断/P0/安全 | 图标 + 严重度 + 动作 |
| Stale | 投影滞后/数据可能旧 | 时间 + 刷新 |
| Offline | 本地状态 | 离线图标 + 同步说明 |
| Shadow | 影子/未结算 | 固定 SHADOW 标签，不使用成功色 |

生命周期、Task、审核、资料、Delivery、配置版本和试算不能共用一个“状态颜色映射”；组件接收语义状态，不直接接收领域枚举并猜颜色。

配置版本至少区分：草稿、已校验、已发布、发布中、已停用、历史版本和运行快照。

## 5. 组件分层

```text
Foundations
→ primitives（Button/Input/Popover/Dialog/Table）
→ patterns（FilterBar/ActionPanel/VersionDiff/AsyncOperation）
→ domain views（SlaBadge/EvidenceViewer/TaskCard/DeliveryTimeline）
→ Portal pages
```

Domain view 可以理解领域只读模型，但不能直接调用 API 或持有角色权限。

## 6. 核心组件

| 组件 | 关键要求 |
|---|---|
| `AdminAppShell` | 白色导航、全局头、项目/范围切换、经典专业风 Token |
| `PageHeader` | 面包屑、标题、说明、主操作和版本/范围上下文 |
| `ScopeBar` | 显示当前租户/项目/区域范围及服务端更新时间 |
| `SummaryStrip` | 3～6 个与当前任务直接相关的摘要，不形成 KPI 墙 |
| `WorkQueueCard` | count、severity、due risk、asOf、下钻 |
| `DataTable` | 键盘、固定列、列设置、空态、批量范围、虚拟化 |
| `FilterBar` | 可分享查询、重置、保存视图、敏感值处理 |
| `AllowedActionBar` | actionCode、obligations、提交/operation 状态 |
| `RightContextRail` | 风险、责任、版本、影响和最近活动，不重复主区 |
| `AsyncOperationPanel` | 进度、目标资源、失败原因、恢复链接 |
| `VersionConflictPanel` | 本地/服务器版本差异和重新确认 |
| `VersionSummary` | 当前发布版、草稿、运行快照和版本状态 |
| `VersionTimeline` | 发布人、时间、版本说明和当前生效标记 |
| `VersionDiffPanel` | 真实资产和业务语义差异，不展示原始 JSON diff |
| `ImpactAnalysisPanel` | 新旧工单、项目、网点、流程和引用影响 |
| `FreshnessIndicator` | asOf/checkpoint/FRESH/LAGGING/UNKNOWN |
| `SlaIndicator` | dueAt、剩余/超时、暂停和政策版本入口 |
| `Timeline` | 业务事件分组、技术细节折叠、深链 |
| `BusinessProgress` | Workflow 阶段和当前 Task 分离表达 |
| `ResponsibilityChain` | 总部、网点、师傅和当前责任状态 |
| `WorkflowCanvas` | 节点、连线、网关、键盘和等价列表编辑 |
| `WorkflowPropertyPanel` | Task、角色、表单、资料、SLA、完成和异常配置 |
| `TaskTemplateDetailPanel` | 执行、分配、输入、动作、升级和引用关系 |
| `EvidenceViewer` | 图片/视频/文档、版本、校验、权限下载 |
| `ReviewDecisionPanel` | 单项决定、标准原因、草稿和 targetVersion |
| `RuleExplanation` | 硬过滤、评分、规则节点和输入版本 |
| `UploadQueue` | 分片进度、网络策略、重试、冲突和本地保留 |
| `OfflineBanner` | 本地/服务器确认状态、最后同步和入口 |
| `HighRiskConfirmation` | 影响、原因、审批、MFA、不可逆说明 |

## 7. 表格

- 列头、排序和筛选可键盘操作；
- 固定主标识列，横向滚动不丢对象上下文；
- 数字/金额右对齐，时间显示时区；
- 状态包含文本；
- 行点击与行内按钮不冲突；
- 虚拟列表保留读屏可理解的总量/位置；
- 批量全选明确“当前页”或“查询全部”；
- 服务器排序/筛选时展示 loading，不保留错误旧计数；
- 敏感字段复制和导出分别授权；
- Admin 标准行高 44～48px，紧凑模式不得降低可读性和点击范围。

## 8. 表单

- label 永久可见，placeholder 不代替 label；
- 必填、帮助、单位、限制和错误与字段程序关联；
- 服务端错误定位到字段/资料槽位，页面顶端同时给出摘要；
- 动态显隐时管理焦点并说明新增要求；
- 数字避免隐式单位和自动四舍五入；
- 日期时间显示业务时区；
- 长表单自动保存草稿并显示状态；
- 离开未保存内容前提示；
- 高风险表单禁用浏览器自动填充敏感字段（按场景）；
- 内部 ID、编码和引用通过业务目录选择器处理；
- 已发布不可变版本使用只读展示，不伪装成可编辑表单。

## 9. Modal、Drawer 与页面

| 容器 | 适用 |
|---|---|
| Popover | 简短解释/低风险选择 |
| Drawer | 不离开列表的只读预览或短动作 |
| Modal | 单一明确确认，内容不长 |
| Full page | 多步骤、高风险、需深链、需版本/历史上下文 |

改派、审核工作区、工作流编辑、任务模板复杂编辑、配置发布、事实更正、rollout 和大批量操作必须使用全页或专用流程，不能塞进通用 Modal。

## 10. 媒体与审核

- 支持原图缩放、旋转、全屏、前后版本并列和缩略图导航；
- 图片加载失败显示文件状态/checksum 摘要，不自动跳过；
- OCR 框与机器判断可切换，避免遮挡原图；
- 视频提供字幕/转写（可用时）、时长和关键帧；
- 键盘可切换资料、通过/驳回，但提交决定需明确确认；
- 下载原件与在浏览器查看分开授权；
- 水印图和原图关系可见。

## 11. 响应式边界

Admin 以桌面高密度为主：

- 1440×1024 为关键视觉评审尺寸；
- 1280px 必须可操作，允许收起导航、把右侧面板转为 Drawer；
- 小于 1024px 只保证查询和少量低风险操作；
- Workflow 设计、配置发布和高密度审核应提示使用桌面端。

Network 支持桌面/平板/移动快速动作。Technician 使用原生/跨端移动布局，不能直接缩放 Admin 表格。

## 12. 可访问性基线

目标遵循 WCAG 2.2 AA 等价要求，并结合企业内部环境验证：

- 所有交互键盘可达；
- 焦点顺序、可见焦点和 Modal 焦点圈正确；
- 图标按钮有可访问名称；
- 表单错误由读屏宣告；
- 颜色对比满足目标；
- 颜色不是唯一状态；
- 动画支持减少动态效果；
- 字体放大/系统缩放不截断关键动作；
- 移动触控目标和间距足够；
- 图表有表格/文字替代；
- 倒计时不高频打扰读屏；
- 超时会话允许在安全范围延长；
- Workflow 画布提供键盘操作或等价的节点列表编辑方式。

## 13. 文案

使用业务动词：“提交勘测资料”“确认预约”“重新回传”，避免“确定”“处理”“操作成功”。

错误文案包含：对象、发生原因（可安全公开）、数据是否已保存、下一步和 correlationId。禁止责怪用户或暴露技术堆栈。

术语来自 glossary 和配置显示名。项目可覆盖业务标签，但 actionCode/pageId/FieldCode 语义稳定。

## 14. 日期、数字与金额

- 存储 UTC，界面按项目/用户指定时区显示并注明；
- 相对时间旁可查看绝对时间；
- 预约窗口显示开始/结束与时区；
- 数量显示单位和精度；
- 金额显示币种、税口径和 SHADOW/正式属性；
- 空值显示“未提供/不适用/未知”，不能统一显示 0 或 `--`；
- 复制值使用原始规范格式，但受字段权限控制。

## 15. 设计资产治理

每个组件包含：用途、anatomy、variants、states、keyboard、accessibility、content、responsive、token 和代码映射。

- 设计 token 版本化；
- 破坏性组件变更有迁移指南；
- Figma/代码组件通过稳定 componentId/Code Connect（若采用）关联；
- Storybook/组件文档覆盖 loading/empty/error/permission/offline；
- Portal 不复制后私改核心组件语义；
- 新业务状态先映射语义，不随意新增颜色；
- Admin 实际页面批准后再建立视觉金标，概念效果图不直接作为自动截图基线。

## 16. 质量验证

- 自动：lint、typecheck、视觉回归、a11y 扫描、token 禁用值扫描；
- 人工：键盘、读屏、缩放、颜色、移动真机、弱网；
- 业务：审核员高频操作、网点负责人快速分配、师傅现场单手操作；
- 安全：敏感字段、缓存、截图、下载和深链；
- 性能：大列表、30 项资料、多版本、复杂 Workflow 和长时间线。

自动扫描通过不等于可访问性完成；关键旅程必须人工验证。
