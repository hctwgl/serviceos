---
title: ServiceOS 统一领域语言
version: 0.1.0
status: Proposed
---

# ServiceOS 统一领域语言

## 1. 目的

本文件规定 ServiceOS 在产品、代码、数据库、接口、事件、测试和运营口径中使用的统一领域语言。目标不是追求抽象，而是让真实业务词汇具有稳定、唯一、可实现的含义。

## 2. 核心语言

### 2.1 WorkOrder（工单）

一次由外部车企订单、内部创建或售后触发产生的独立服务履约实例。

工单负责：

- 内部唯一身份与外部业务编号；
- 车企、品牌、项目、服务产品和业务类型；
- 客户、车辆、设备和服务地址的稳定引用或接单快照；
- 锁定的配置包版本；
- 工单级生命周期：激活、暂停、取消、履约完成、关闭和恢复；
- 当前阶段、风险、责任和进度的查询投影。

工单不负责保存：

- 每一版照片或文件；
- 每一次审核决定；
- 派单候选评分明细；
- 预约和上门历史；
- 计价运行与结算行明细。

禁止替代名称：`Fulfillment Aggregate`、`Ticket`、`Case`、`Job`。

`Fulfillment` 在 ServiceOS 中表示“履约”这一行为或结果，例如 `FulfillmentFact`，不是工单的同义词。

### 2.2 ServiceRequest（服务请求）

产生一个或多个工单的上游需求记录。它可以来自车企订单、客服登记、投诉、维修重开或内部运营。

一个服务请求可拆分为多个工单，例如安装完成后产生独立维修工单。禁止通过无限重开同一工单表达跨多年、不同责任和不同结算边界的服务活动。

### 2.3 ServiceProduct（服务产品）

项目对外承诺并可配置执行的服务类型，例如：

- 纯勘测；
- 勘测后安装；
- 维修；
- 拆装；
- 返工；
- 收费维修。

`ServiceProduct` 决定可选择的流程、表单、资料、SLA、派单和计价配置，但不等于某一份配置版本。

### 2.4 ConfigurationBundle（配置包）

一组已发布、可校验、版本精确的配置资产引用。工单创建时必须锁定一个配置包版本。

配置包至少可以引用：

- Workflow；
- Form；
- Evidence Template；
- Rule；
- SLA Policy；
- Dispatch Policy；
- Review Policy；
- Receivable Pricing；
- Payable Pricing；
- Notification Policy；
- Integration Mapping。

禁止使用“当前配置”解释历史工单。配置包升级默认只影响新工单；运行中工单迁移必须显式、可审计并经过兼容性校验。

### 2.5 StageInstance（阶段实例）

工单流程中某个业务阶段的一次运行实例，例如勘测、安装、整改或回传阶段。

阶段是任务图的业务分组和进度边界，不是统一待办。阶段重开必须创建新实例，不覆盖旧实例。

### 2.6 Task（任务）

需要系统、角色或具体人员执行并产生明确结果的工作单元。Task 是统一待办、责任人、SLA、重试和人工接管的执行外壳。

Task 负责：

- 类型、状态和所属阶段；
- 候选人、执行人或自动执行器；
- 允许动作和完成条件；
- SLA 引用；
- 重试、失败和人工接管；
- 关联业务对象的稳定标识。

Task 不负责复制关联聚合的业务事实。例如审核任务引用 `ReviewCaseId`，但审核决定只保存在 ReviewCase 中。

### 2.7 WorkflowDefinition / WorkflowInstance（流程定义/流程实例）

流程定义是版本化任务图；流程实例是某个工单对该定义版本的运行记录。

流程负责确定何时创建、激活、阻塞或取消任务，不直接成为客户、资料、审核、派单或结算事实源。

### 2.8 Evidence（履约证据）

用于证明履约事实、满足车企要求或支持审核结算的资料统称，包括：

- 照片；
- 视频；
- PDF 或纸质单据扫描件；
- 电子签字；
- 音频；
- OCR、条码、二维码识别结果；
- GPS、拍摄时间、水印和设备信息；
- 系统生成报告。

`EvidenceSlot` 表示某个资料要求的运行时槽位；`EvidenceItem` 表示一个逻辑资料项；`EvidenceRevision` 表示该资料项的不可变版本。

禁止用 `Attachment` 表示业务证据。Attachment 仅可作为传输或文件技术概念。

### 2.9 ReviewCase（审核案例）

对一个明确、不可变审核对象进行一次完整审核闭环的业务记录。审核对象可以是资料版本、资料集合快照、表单提交、履约事实或试算结果。

ReviewCase 可以包含多次 `ReviewDecision`，以表达驳回、补传、复审、车企反馈和强制通过。

`Validation` 表示规则、OCR、AI 或系统执行的自动校验能力。自动校验可以为 ReviewCase 提供输入，但不替代人工审核和车企审核的领域语义。

禁止把审核结果直接压缩为 `work_order.review_status`。

### 2.10 CorrectionCase（整改案例）

因审核驳回、质量问题、投诉或车企反馈而产生的整改闭环。它记录整改范围、责任、时限、证据版本和完成结果。

整改必须引用被驳回的明确对象与版本；补传产生新版本，不覆盖历史证据。

### 2.11 DispatchRequest / DispatchDecision / ServiceAssignment

- `DispatchRequest`：一次需要选择服务网点或师傅的派单请求；
- `DispatchDecision`：某次不可变的候选过滤、评分和选择结果；
- `ServiceAssignment`：被激活的实际服务责任关系。

派单不是直接修改网点或师傅字段。人工指定、自动决策、改派和取消分配都必须产生可审计事实。

### 2.12 Appointment / Visit / FieldOperation

- `Appointment`：约定的服务时间窗口；
- `Visit`：一次实际或计划上门；
- `FieldOperation`：上门期间执行的勘测、安装、维修、拆装等现场动作。

一次预约可能改约；一次任务可能多次上门；一次上门可以包含多个现场动作。三者不得合并为一个时间字段或状态字段。

### 2.13 SlaInstance（SLA 实例）

某个任务或业务承诺按精确策略版本创建的计时事实。它记录开始、截止、暂停、恢复、预警、超时和升级历史。

SLA 不是通过查询 `created_at + 固定天数` 临时计算；夜间、节假日、用户原因和外部阻塞必须由策略与时钟实例显式处理。

### 2.14 FulfillmentFact（履约事实）

从已完成任务、表单、证据、上门和审核结果中提取的标准化、可追溯事实，例如线缆米数、立柱数量、二次上门次数和偏远区域。

履约事实是对上和对下计价共享的事实输入，但对上、对下金额必须分别计算。

### 2.15 CalculationRun / ChargeItem / SettlementStatement

- `CalculationRun`：使用精确事实快照和计价版本执行的一次可解释计算；
- `ChargeItem`：某一计价规则产生的费用项；
- `SettlementStatement`：对账和结算的权威单据；
- `Adjustment`：红冲、补差、追责或争议处理产生的显式调整。

禁止在 WorkOrder 上维护可随规则变化而覆盖的“当前对上金额”和“当前对下金额”。

### 2.16 Integration Adapter（集成适配器）

隔离外部车企协议与 ServiceOS 领域模型的反腐层。适配器负责认证、签名、Schema 校验、幂等、字段映射、错误归一化和协议版本。

外部 DTO、枚举和状态码不得直接进入核心领域。

## 3. 角色语言

| 中文业务词 | 统一英文名 | 定义 |
|---|---|---|
| 跟进责任人 | Coordinator | 对工单进展负责的内部协调角色，不等同于实际施工人 |
| 网点 | ServiceNetwork | 承接区域和品牌服务能力的合作组织 |
| 师傅 | Technician | 执行现场作业的个人 |
| 品牌负责人 | BrandOwner | 对品牌整体业务、风险和指标负责 |
| 项目经理 | ProjectManager | 按品牌和区域管理履约进展与异常 |
| 审核员 | Reviewer | 对 ReviewCase 作出人工决定的人 |
| 结算专员 | SettlementSpecialist | 负责对账、结算和异常处理 |

代码和接口中不使用含糊的 `Follower`、`Master`、`Worker` 表达上述角色。

## 4. 状态语言

- 工单生命周期只表达工单级稳定状态；
- 待派单、待预约、勘测中、审核中等属于阶段或任务投影；
- “完成”必须指明对象：Task Completed、Stage Completed、WorkOrder Fulfilled 或 WorkOrder Closed；
- “关闭”不等于“履约完成”，强制关闭、取消和正常完成必须可区分；
- 已通过后重新驳回必须创建新决定或重开案例，禁止原地改写历史结论。

## 5. 禁止的歧义词

| 禁止或限制词 | 原因 | 应使用 |
|---|---|---|
| 单子、票据、Case | 无法确定领域边界 | WorkOrder、ReviewCase 或 SettlementStatement |
| 附件 | 仅是技术传输概念 | Evidence 或 StoredFile |
| 当前规则 | 无版本语义 | 精确配置资产 code/version |
| 审核状态 | 容易压平多轮历史 | ReviewCase + ReviewDecision |
| 派给网点 | 缺少决策与责任事实 | DispatchDecision + ServiceAssignment |
| 工单完成 | 含义不明确 | WorkOrder Fulfilled 或 WorkOrder Closed |
| 重开原任务 | 破坏历史 | 创建新 StageInstance/Task 并关联原因 |
