---
title: Network Portal 经典专业协作风视觉与交互基线
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-20
owner: Product Owner
approvedStyle: 方案 A｜经典专业协作风
---

# Network Portal 经典专业协作风视觉与交互基线

## 1. 决策结论

产品负责人已批准 ServiceOS 网点端采用：

> **方案 A｜经典专业协作风**

该方案继承 Admin 的企业级可信感，但降低治理复杂度，强化协作、调度、责任和时效。

网点端必须像一个高效的服务协作工作台，而不是：

- Admin 的菜单删减版；
- 数据库和接口调试页面；
- 单纯的工单列表；
- KPI 大屏；
- 师傅 App 的桌面版本。

## 2. 产品目标

网点负责人打开系统后，应在一分钟内回答：

1. 当前有多少工单待分配师傅；
2. 今天有哪些预约和上门；
3. 哪些任务即将超时或已经超时；
4. 哪些资料或整改正在阻塞完结；
5. 哪些师傅可用、忙碌、冲突或资质受限；
6. 当前网点产能是否接近上限；
7. 下一步最应该处理什么。

## 3. 适用角色与设备

### 3.1 角色

- 网点负责人；
- 获授权的网点运营人员；
- 获授权的网点客服/协调人员；
- 获授权的资料补充人员；
- 获授权的师傅与资质管理员。

当前产品不假设独立“网点调度员”岗位；能力通过 Capability 组合。

### 3.2 设备

- 桌面浏览器：完整体验；
- 平板横屏：完整或接近完整体验；
- 平板竖屏：核心查询、分配、联系和整改；
- 手机浏览器：快速查看、分配、联系和异常处置；
- 现场离线表单、GPS、相机和大文件上传仍由 Technician 客户端承担。

## 4. 视觉语言

### 4.1 气质

必须呈现：

- 稳定、专业、可信赖；
- 数据清晰但不过度密集；
- 任务和责任优先；
- SLA、风险和阻塞显著；
- 主操作明确；
- 协作状态容易理解；
- 长时间使用不疲劳。

禁止呈现：

- 营销插画和渐变大背景；
- 霓虹、玻璃拟态和夸张阴影；
- 深色作为默认模式；
- 所有卡片同等强调；
- 大量彩色 KPI 墙；
- 技术 ID、原始枚举、JSON 和接口字段；
- 通过花哨动画掩盖状态不明确。

### 4.2 语义 Token

网点端共享 ServiceOS 品牌 Token，并采用以下 Portal Profile：

| Token | 建议值 | 用途 |
|---|---:|---|
| `network-color-primary` | `#1677FF` | 主按钮、选中、链接、当前步骤 |
| `network-color-canvas` | `#F5F7FA` | 页面背景 |
| `network-color-surface` | `#FFFFFF` | 导航、卡片、表格、抽屉 |
| `network-color-surface-subtle` | `#FAFAFA` | 次级区域、只读区 |
| `network-color-border` | `#E5E7EB` | 卡片、表格和分隔 |
| `network-color-text-primary` | `#111827` | 标题和主要值 |
| `network-color-text-secondary` | `#4B5563` | 说明和标签 |
| `network-color-success` | `#16A34A` | 已完成、可用、已确认 |
| `network-color-warning` | `#D97706` | 忙碌、风险、待确认 |
| `network-color-critical` | `#DC2626` | 超时、失败、阻断 |
| `network-color-info` | `#1677FF` | 进行中和提示 |

品牌蓝不得替代状态颜色。状态必须同时使用文字、图标和颜色表达。

### 4.3 字体与密度

中文字体使用系统字体栈：

```text
-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
"Microsoft YaHei", "Noto Sans CJK SC", sans-serif
```

建议：

| 用途 | 字号 | 字重 |
|---|---:|---:|
| 页面标题 | 22～24px | 600 |
| 对象标题 | 18～20px | 600 |
| 分区标题 | 16px | 600 |
| 正文/表格 | 14px | 400/500 |
| 辅助信息 | 12px | 400 |
| 概览数字 | 24～30px | 600 |

密度：

- 表格行高 44～48px；
- 控件高度 32～36px；
- 卡片内边距 16～20px；
- 页面区块间距 16px；
- 页面水平边距 20～24px；
- 卡片圆角 6～8px；
- 阴影只用于浮层和明确层级。

## 5. 应用壳

### 5.1 左侧导航

默认宽度 184～208px，白色或极浅背景。

一级菜单固定为：

1. 网点工作台；
2. 本网点工单；
3. 任务队列；
4. 师傅调度；
5. 预约协同；
6. 资料整改；
7. 师傅与资质；
8. 产能状态；
9. 异常中心；
10. 消息中心；
11. 设置。

规则：

- 菜单名称使用业务中文；
- 当前菜单使用浅蓝底、蓝色图标和文字；
- 不出现 Admin 的项目配置、角色授权、全租户审计和连接器治理；
- 网点切换由服务端 Context 提供，只有多网点成员关系时展示；
- 不通过前端角色名称决定菜单。

### 5.2 顶部栏

包含：

- 当前网点业务名称和切换；
- 全局搜索；
- 通知和待办数量；
- 帮助入口；
- 当前用户。

搜索支持：工单号、客户脱敏信息、师傅姓名和服务地址摘要。不得搜索或展示其他网点数据。

### 5.3 页面头

标准结构：

```text
Breadcrumb
→ PageTitle + Description
→ Scope/Date Controls
→ PrimaryActions
```

页面头不重复左侧导航；主操作不超过 1～2 个。

## 6. 全局页面模式

### 6.1 工作台

```text
AppShell
→ PageHeader
→ SummaryStrip
→ TodayTimeline / PriorityQueue / TechnicianLoad
→ Appointment / SLA / QuickActions
```

### 6.2 列表页

```text
PageHeader
→ SummaryStrip（可选）
→ FilterBar + SavedViews
→ BusinessDataTable
→ DetailDrawer / AssignmentDrawer
→ Pagination
```

表格固定：主标识、当前任务、责任师傅、预约、SLA、状态和操作。

### 6.3 工单工作区

```text
ObjectHeader + SLA
→ FulfillmentProgress
→ CurrentTask / Appointment / Evidence / Risk
→ Timeline
→ RightContextRail or AssignmentDrawer
```

首屏必须看到：工单、当前阶段、当前任务、责任、预约、SLA、风险和下一步。

### 6.4 调度抽屉

师傅分配和改派采用右侧宽抽屉，避免离开工单上下文。

抽屉宽度建议 420～520px，包含：

- 可分配/最近分配；
- 搜索和筛选；
- 师傅候选卡；
- 冲突和能力缺口；
- 已选对象；
- 影响说明；
- 确认操作。

复杂跨网点调度不属于 Network Portal。

## 7. 业务状态呈现

### 7.1 工单与任务

用户看到业务状态：

- 待分配；
- 待预约；
- 已预约；
- 上门中；
- 待资料；
- 待整改；
- 待审核；
- 已完成；
- 已取消。

不得直接展示无法理解的内部枚举。

### 7.2 师傅状态

统一为：

- 可接单；
- 服务中；
- 忙碌；
- 暂停接单；
- 资质受限；
- 已停用。

负载通过数值和进度条表达，同时显示统计口径。

### 7.3 异步状态

分配、改派、通知或资料提交可能返回异步 Operation。页面必须区分：

```text
提交处理中
→ 责任激活中
→ 已完成
或
→ 失败/需人工处理
```

不得在 saga 未完成时显示“分配成功”。

## 8. 中文文案

推荐：

- 分配师傅；
- 改派师傅；
- 联系客户；
- 修改预约；
- 查看整改要求；
- 代补资料；
- 暂停接单；
- 申请调整产能；
- 查看异常原因。

禁止：

- `assignTechnician`；
- `networkId`；
- `scopeRef`；
- `TaskAssignment`；
- `ServiceAssignment`；
- `aggregateVersion`；
- 原始英文异常。

技术信息按权限进入“高级信息/诊断”。

## 9. 安全与数据最小化

- 只展示当前有效 Network Context 下的数据；
- 改派后旧网点的列表、详情、下载和深链必须失效；
- 客户电话和地址按当前任务需要脱敏或解密；
- 不展示其他网点候选、负载、评分、价格和成本；
- 不展示总部内部负责人、敏感备注和连接器凭据；
- 导出、下载和代办动作必须增强审计；
- 页面缓存和本地持久化不得突破服务端范围。

## 10. 响应式

### 10.1 1440×1024

标准评审尺寸，完整展示导航、概览、主表格、右侧上下文和关键操作。

### 10.2 1280px

- 可收起左侧导航；
- 右侧上下文转 Drawer；
- 表格允许横向滚动；
- 固定主标识和操作列；
- 不隐藏 SLA、状态和主操作。

### 10.3 平板

- 工作台改为两列；
- 师傅调度使用全屏 Drawer；
- 工单工作区卡片纵向排列；
- 支持快速分配、联系、预约和整改；
- 不承诺复杂批量操作。

### 10.4 手机 Web

只保证快速查看、分配、联系、异常和消息；现场作业转 Technician 客户端。

## 11. 必须设计的状态

每个核心页面必须覆盖：

- 首次加载；
- 局部刷新；
- 空数据；
- 无权限；
- 只读；
- 当前网点已失效；
- 工单已改派；
- 师傅候选为空；
- 师傅资质不足；
- 时间冲突；
- SLA 即将超时/已超时；
- 异步处理中；
- 并发冲突；
- 投影延迟；
- 服务失败；
- 操作失败但业务事实已部分建立。

错误必须说明：发生了什么、是否已保存、下一步如何恢复。

## 12. 实现组件建议

- `NetworkAppShell`；
- `NetworkScopeSelector`；
- `NetworkSummaryStrip`；
- `PriorityTaskTable`；
- `TodayTimeline`；
- `TechnicianLoadPanel`；
- `SlaRiskList`；
- `FulfillmentProgress`；
- `CurrentTaskCard`；
- `AppointmentCard`；
- `EvidenceProgressCard`；
- `NetworkTimeline`；
- `TechnicianAssignmentDrawer`；
- `TechnicianCandidateCard`；
- `AssignmentImpactSummary`；
- `CorrectionQueue`；
- `CapacityStatusPanel`；
- `AllowedActionBar`；
- `ProblemPresenter`。

组件只负责展示和交互，权限、范围、候选和 allowed-actions 由服务端提供。

## 13. 与效果图的关系

批准效果图确定：

- 白色导航、企业蓝和浅色工作区；
- 工作台的概览、时间轴、待分配、师傅负载、预约和 SLA 布局；
- 工单工作区的履约进度、当前任务、预约、风险、资料和动态；
- 分配师傅使用右侧宽抽屉，候选卡展示负载、冲突、距离和技能。

效果图中的名称、工单号、数字和示例头像不是生产数据。真实实现必须使用 API DTO、统一中文 Presenter、Capability、Scope 和 allowed-actions。
