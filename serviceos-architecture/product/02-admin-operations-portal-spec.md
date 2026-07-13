---
title: 总部运营后台产品规格
version: 0.1.0
status: Proposed
---

# 总部运营后台产品规格

## 1. 用户与默认工作入口

| 用户模板 | 默认入口 | 主要关注 | 不自动获得 |
|---|---|---|---|
| 品牌负责人 | 品牌风险工作台 | 全品牌 SLA、异常、产能、履约指标 | 技术超级权限、金额调整审批 |
| 项目经理 | 项目履约工作台 | 区域积压、派单失败、改派、网点协调 | 资料强制通过 |
| 跟进专员 | 我的待办 | 工单跟进、数据补充、催办 | 超范围品牌/区域 |
| 客服经理 | 客服队列 | 联系、预约、审核、车企驳回协调 | 对下成本 |
| 客服/审核员 | 我的审核/待办 | 单项审核、整改跟踪、用户/网点联络 | 修改师傅提交字段、强制通过 |
| 风控/网络管理 | 服务网络工作台 | 网点准入、资质、停派、容量和质量 | 工单现场提交 |
| 结算专员 | 影子试算工作台 | 不可计算、差异、导出 | M5 正式结算写入 |
| 运营 | 运营分析 | 业务指标和趋势 | 默认执行现场履约动作 |
| 系统运维 | 平台运行工作台 | 集成、队列、异常、审计 | 业务强制操作和金额审批 |

这些是首页模板，不是固定角色授权。最终页面、数据和动作以 capability/data scope/feature gate 为准。

## 2. 页面目录

### 2.1 工作台与工单

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `ADMIN.WORKBENCH` | `/workbench` | 个人/角色工作台 | 是 | `task.read`、`workOrder.read` |
| `ADMIN.WORK_ORDER.LIST` | `/work-orders` | 工单列表 | 是 | `workOrder.read` |
| `ADMIN.WORK_ORDER.WORKSPACE` | `/work-orders/:id` | 工单工作区 | 是 | `workOrder.read` + 动态 actions |
| `ADMIN.TASK.QUEUE` | `/tasks` | 任务队列 | 是 | `task.read` |
| `ADMIN.TASK.DETAIL` | `/tasks/:id` | 任务详情/动作 | 是 | `task.read` + 动态 actions |
| `ADMIN.TIMELINE` | `/work-orders/:id/timeline` | 完整业务时间线 | 是 | `workOrder.readTimeline` |

### 2.2 履约协作

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `ADMIN.DISPATCH.QUEUE` | `/dispatch` | 派单队列 | 是 | `dispatch.read` |
| `ADMIN.DISPATCH.DETAIL` | `/dispatch/:id` | 候选/评分/人工选择 | 是 | `dispatch.read`、`workOrder.reassignNetwork` |
| `ADMIN.APPOINTMENT.QUEUE` | `/appointments` | 待预约/联系队列 | 是 | `appointment.read` |
| `ADMIN.APPOINTMENT.DETAIL` | `/appointments/:id` | 预约修订与联系历史 | 是 | `appointment.manage` |
| `ADMIN.FIELD_VISIT.VIEW` | `/visits/:id` | 上门/现场只读视图 | 是 | `fieldwork.read` |

### 2.3 审核与异常

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `ADMIN.REVIEW.QUEUE` | `/reviews` | 审核队列 | 是 | `evidence.review` |
| `ADMIN.REVIEW.WORKSPACE` | `/reviews/:id` | 审核工作区 | 是 | `evidence.review` |
| `ADMIN.CORRECTION.QUEUE` | `/corrections` | 整改跟踪 | 是 | `correction.read` |
| `ADMIN.EXCEPTION.QUEUE` | `/exceptions` | 运营异常队列 | 是 | `exception.read` |
| `ADMIN.EXCEPTION.DETAIL` | `/exceptions/:id` | 异常诊断与恢复 | 是 | `exception.handle` |
| `ADMIN.SLA.QUEUE` | `/sla` | SLA 预警/超时 | 是 | `sla.read` |

### 2.4 集成、事实与试算

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `ADMIN.INTEGRATION.INBOUND` | `/integration/inbound` | 收单/回执记录 | 是 | `integration.read` |
| `ADMIN.INTEGRATION.OUTBOUND` | `/integration/outbound` | 回传/通知交付 | 是 | `integration.read` |
| `ADMIN.INTEGRATION.DETAIL` | `/integration/deliveries/:id` | attempt/ack/重放 | 是 | `integration.read`、`integration.replay` |
| `ADMIN.FACT.VIEW` | `/work-orders/:id/facts` | 履约事实及血缘 | 是 | `fact.read` |
| `ADMIN.CALCULATION.QUEUE` | `/calculations` | 影子试算/不可计算队列 | 是 | `pricing.calculate` |
| `ADMIN.CALCULATION.DETAIL` | `/calculations/:id` | 费用明细/解释/比较 | 是 | `pricing.read` |
| `ADMIN.SETTLEMENT` | `/settlement` | 正式对账结算 | 二期 | `FORMAL_SETTLEMENT` + settlement capabilities |

### 2.5 网络、项目与治理

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `ADMIN.NETWORK.LIST` | `/networks` | 网点列表 | 是 | `network.read` |
| `ADMIN.NETWORK.DETAIL` | `/networks/:id` | 覆盖/资质/容量/表现 | 是 | `network.read` |
| `ADMIN.TECHNICIAN.LIST` | `/technicians` | 师傅与资质 | 是 | `technician.read` |
| `ADMIN.PROJECT.LIST` | `/projects` | 项目与服务产品 | 是 | `project.read` |
| `ADMIN.CONFIGURATION.LIST` | `/configuration/assets` | 配置资产 | 是 | `configuration.read` |
| `ADMIN.CONFIGURATION.EDITOR` | `/configuration/assets/:id` | 草稿/校验/发布 | 是 | `configuration.edit/approve/publish` |
| `ADMIN.AUTHORIZATION` | `/governance/access` | 组织/角色/授权 | 是 | `authorization.manage` |
| `ADMIN.AUDIT` | `/governance/audit` | 审计查询 | 是 | `audit.read` |
| `ADMIN.ROLLOUT` | `/governance/rollout` | cohort/Gate/切换 | 试点 | `rollout.manage` |

## 3. 工作台

### 3.1 页面目标

回答四个问题：

1. 我现在必须处理什么？
2. 哪些事情即将/已经违反 SLA？
3. 自动化在哪里失败并等待人工？
4. 我的授权范围和数据更新时间是什么？

### 3.2 布局

```text
范围条：品牌 / 项目 / 区域 / 时间（只显示有效授权范围）
P0/P1 风险横幅
我的待办：按 dueAt/severity 排序
角色队列：派单失败 / 待审核 / 回传失败 / 不可试算
趋势摘要：只读、可下钻到同口径列表
近期处理：快速回到上次工作上下文
```

工作台指标必须显示口径、窗口、asOf 和下钻链接。不能把“累计工单数”与“当前待办数”放在同一视觉语义下。

## 4. 工单列表

### 4.1 默认列

- 平台/外部工单号；
- 品牌/项目/服务产品；
- 区域；
- 用户（脱敏）；
- 当前阶段 + 当前 Task；
- 当前网点/师傅；
- Task dueAt/SLA 风险；
- 阻塞/异常/回传摘要；
- 最近业务事件时间。

### 4.2 筛选

按项目、品牌、服务产品、区域、网点、师傅、生命周期、Stage、TaskType/TaskState、SLA 风险、资料/审核/集成/试算状态、风险等级和创建时间组合筛选。

列表不提供一个含义不清的“安装中”筛选替代上述维度。

### 4.3 行操作

默认只提供“打开工作区”。快速动作仅用于低风险、输入很少且上下文明确的动作，例如领取 Task；改派、关闭、审核和恢复进入专用页面/抽屉。

## 5. 工单工作区

### 5.1 Header

- 外部/平台工单号及复制；
- 服务产品、品牌项目、风险；
- 生命周期、当前阶段、当前 Task（分别显示）；
- SLA 倒计时/超时；
- 当前责任人、网点、师傅；
- 投影新鲜度；
- 主 allowed-action 和“更多”。

### 5.2 概览卡

| 卡片 | 内容 | 来源 |
|---|---|---|
| 当前处理 | 当前 Task、执行人、完成条件、dueAt | Task |
| 用户与地点 | 脱敏联系、地址、地图入口 | 客户/地址引用 |
| 车辆与设备 | VIN/车型/桩 SN/型号（按权限） | 资产引用/表单 |
| 服务责任 | 网点、师傅、改派时间 | ServiceAssignment |
| 最近异常 | 阻塞、P0/P1 Exception、失败回传 | 各权威对象投影 |
| 下一步 | allowed-actions、等待对象和预计条件 | Task/Workflow 投影 |

卡片数字可点击来源，不能直接编辑权威事实。

### 5.3 任务与阶段 Tab

使用阶段分组的 Task 时间轴，展示 TaskType、状态、执行人、SLA、输入/结果版本和 attempt。自动 Task 展示重试和人工接管；流程 token 只在技术授权下展开。

### 5.4 表单与资料 Tab

按勘测/安装/整改任务分组，显示表单版本、提交版本、资料槽位完成度和机器校验。默认缩略图保护敏感信息；打开原图需要授权和访问审计。

### 5.5 集成与试算 Tab

集成显示 Delivery 而非“同步成功”单字段；试算显示 SHADOW 标识、方向、事实/价格版本、费用明细和差异，不显示未启用的正式结算动作。

## 6. 任务队列

任务队列支持：我的、候选、团队、自动失败、人工接管、已处理。每条 Task 展示 action schema 摘要、dueAt、阻塞和来源工单。

- Claim 使用版本和幂等；
- Task 被他人领取时返回 409 并从当前视图移除；
- 自动失败必须展示失败分类、最后 attempt、下次重试或人工接管；
- 批量领取仅对同 TaskType/策略且服务端允许的任务开放。

## 7. 派单工作区

布局：

```text
左：工单/地址/业务要求
中：候选列表与地图（地图不是唯一入口）
右：硬过滤、评分、容量和推荐解释
底部：自动决策历史 / 手工覆盖 / 改派影响
```

候选必须显示：是否合格、排除原因、评分分解、当前在途/上限、比例偏差、资质、停派和数据时间。黑名单/停派不可通过普通覆盖；授权例外显示 obligations。

改派确认显示：原网点失去访问权、当前师傅/预约/Task 的影响、是否需要取消通知和容量变更。提交后跟踪 activation saga，未完成前不显示“改派成功”。

## 8. 预约队列

按首次联系、待预约、需改约、无法联系、今日上门和异常分组。预约抽屉同时显示联系历史、用户时间偏好、当前师傅和冲突提示。

客服、网点、师傅使用同一 Appointment 版本；发现版本冲突时显示对方最新修订并要求重新确认，不能覆盖。

## 9. 审核工作区

### 9.1 布局

```text
左侧：资料/字段目录与状态
中间：原图/视频/表单查看器
右侧：拍摄要求、示例、OCR/校验、历史版本、决定
底部：通过/驳回所选项、提交整组决定
```

### 9.2 规则

- 审核对象锁定精确版本；
- 允许逐项决定，不允许只提交 overallDecision；
- 驳回必须选适用 reasonCode，可补充说明；
- 已通过项不因其他项驳回而重置；
- 资料出现新版本时阻止对旧版本提交决定并提示刷新；
- 审核员不能在此修改师傅字段或代传；
- ForceApprove 使用独立 action/能力/审批界面；
- 离开存在未提交决定的页面前警告并允许保存个人草稿（草稿不构成决定）。

## 10. 整改跟踪

展示 CorrectionCase、轮次、驳回项、整改 Task、责任人、dueAt、最新补传和再审结果。客服可催办、联系和查看；不能直接把 Case 标记完成。

车企驳回先进入客服协调队列，页面提供“确认影响项 → 创建/关联整改 Task → 通知网点/师傅”的受控动作。

## 11. 异常与集成

### 11.1 异常队列

默认按 severity、dueAt、首次发生排序。详情展示 occurrence、来源对象、尝试、处理 Task、当前 owner（来自 Task）、允许的原领域恢复动作和验证结果。

“关闭异常”只有验证成功后出现；普通用户不能只写备注关闭。

### 11.2 Delivery 详情

展示 payload 摘要、mapping/connector 版本、attempt、请求/响应摘要、外部 ack、幂等键、authority/fence 和关联 Task。完整原文/技术错误按敏感权限展开。

Retry/Replay 说明区别：业务 Task 重试继续原 Delivery；人工重放创建 ReplayRequest/新 Delivery。结果 UNKNOWN 时优先查询/对账动作。

## 12. 影子试算

列表区分 RECEIVABLE/PAYABLE、CALCULATED/NOT_CALCULABLE/STALE、当前/候选价格和 SHADOW。详情从总额下钻 ChargeItem → ruleNode → Fact → source/evidence。

- 对下价格仅授权角色可见；
- 候选 override 需要审批引用；
- 导出显著标记“试算/未结算”；
- M5 不出现 CreateStatement/Lock/FinanceHandoff 可用按钮；
- 事实更正 pending 时说明 guard，而非显示零金额。

## 13. 配置资产编辑器

采用“资产元数据 → 草稿 → 校验 → 样本回放 → 差异 → 审批 → 发布”的向导。发布前显示受影响新工单范围、依赖资产、版本摘要和回滚方式。

不同资产使用专用编辑器：流程图、表单 Schema、资料矩阵、决策表、SLA、派单策略、通知模板、价格规则。首期不做任意页面搭建器。

## 14. 批量操作

首期只允许：导出（受限）、批量领取同类 Task、批量通知预演、配置样本回放。批量改派、强制关闭、强制审核和事实更正默认禁止；确需启用必须有专用批次对象、dry-run、逐项结果、审批和恢复方案。

## 15. 页面完成定义

每个 MVP 页面必须具备：

- pageId、路由和 capability；
- 查询/命令契约与错误状态；
- 数据范围和字段权限测试；
- loading/empty/lagging/403/404/409/422/429/503；
- 命令幂等、版本冲突和异步 operation 反馈；
- 可访问名称、键盘操作和焦点恢复；
- 产品埋点不含敏感数据；
- 从页面动作回溯到 M2～M6 验收 ID。
