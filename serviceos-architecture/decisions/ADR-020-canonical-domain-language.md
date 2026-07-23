---
title: ADR-020 核心领域命名与边界稳定策略
version: 0.1.0
status: Proposed
---

# ADR-020：核心领域命名与边界稳定策略

## 1. 背景

ServiceOS 已进入从平台架构设计向真实业务纵向切片过渡的阶段。近期讨论中出现了以下命名漂移：

- 是否将 `WorkOrder` 改名为 `Fulfillment`；
- 是否将 `ReviewCase` 改名为 `Validation`；
- 是否以 Task 或 Workflow 取代工单成为核心事实源；
- 是否把 Evidence 简化为 Attachment；
- 是否将上游车企订单模型直接作为核心领域模型。

如果一级领域名称持续变化，Java 包、数据库表、OpenAPI、事件、测试、产品页面和数据指标会形成多套语言，后续重构成本和业务误解风险都很高。

## 2. 决策

### 2.1 保留 WorkOrder 作为核心履约实例名称

`WorkOrder` 与业务人员、车企接口、运营指标和现有架构事实源一致，因此继续作为核心工单聚合名称。

`Fulfillment` 仅用于表达履约行为、履约结果和标准化履约事实，例如 `FulfillmentFact`，不作为 WorkOrder 的替代名称。

### 2.2 Task 是执行外壳，不取代业务聚合

流程运行时通过 Task 组织待办、责任人、SLA、重试和人工接管。Task 可以引用 ReviewCase、DispatchRequest、Appointment 等业务对象，但不能复制并拥有它们的事实。

WorkflowDefinition 是版本化任务图，WorkflowInstance 负责推进任务图；二者都不成为 WorkOrder、Evidence、Review 或 Settlement 的事实源。

### 2.3 保留 ReviewCase，区分 Validation

`ReviewCase` 表示人工、总部或车企审核闭环；`ReviewDecision` 表示不可变审核决定。

`Validation` 表示规则、OCR、AI 或系统执行的自动校验能力。Validation 可以为 ReviewCase 提供输入，但不能替换人工审核与车企审核的业务概念。

### 2.4 Evidence 是业务概念，Attachment 是技术概念

照片、视频、签字、PDF、OCR、GPS 和系统报告统一称为 Evidence。文件存储和传输层可以使用 StoredFile、Object 或 Attachment 等技术术语，但不能把业务证据降格为普通附件。

### 2.5 履约方案版本与 ConfigurationBundle 是运行时配置锁定单位

工单正式受理时匹配唯一履约方案并锁定其生效版本（FulfillmentPlanVersion）；该方案版本对应锁定精确 ConfigurationBundle。流程、表单、资料、SLA、派单、审核、价格和集成映射等配置资产均通过方案版本对应的 Bundle 引用。

历史工单不得使用“当前配置”或“当前生效版本”重新解释。配置迁移必须显式、可校验并可审计。多方案匹配与版本绑定见 DEC-007 与 AD-014。

### 2.6 一级领域名称受 ADR 保护

下列概念的重命名、合并或拆分必须新增 ADR：

- WorkOrder；
- ServiceRequest；
- ServiceProduct；
- ConfigurationBundle；
- FulfillmentPlan / FulfillmentPlanVersion；
- WorkflowInstance；
- Task；
- EvidenceItem / EvidenceRevision；
- ReviewCase / ReviewDecision；
- DispatchRequest / DispatchDecision / ServiceAssignment；
- Appointment / Visit / FieldOperation；
- SlaInstance；
- FulfillmentFact；
- CalculationRun；
- SettlementStatement。

## 3. 采用的理由

- 与真实业务口径一致，降低培训和沟通成本；
- 与现有 0.15.0 架构基线兼容，避免无业务收益的大规模重命名；
- 保留平台化边界，同时不过度抽象；
- 允许 Task-driven 运行时，而不把 Task 误当成所有业务事实源；
- 允许 AI/OCR 自动校验演进，而不破坏人工审核和车企审核模型；
- 支持未来多车企、多业务类型和长期售后演进。

## 4. 被否决的方案

### 4.1 将 WorkOrder 全面改名为 Fulfillment

否决原因：

- 与业务人员日常语言和车企订单语义脱节；
- 需要同时重命名代码、数据库、接口、事件、报表和文档；
- “履约”更适合作为行为和事实集合，而不是单一工单实例名称；
- 缺少足够收益证明。

### 4.2 将 ReviewCase 全面改名为 Validation

否决原因：

- 自动校验、人工审核和车企审核不是同一概念；
- Validation 无法自然表达驳回、补传、多轮复审和强制通过；
- 容易让系统把 AI 结果误当作最终业务决定。

### 4.3 以 Task 作为唯一业务对象

否决原因：

- Task 是执行外壳，不能自然承载资料版本、审核决定、派单候选、计价运行和结算单据；
- 会形成巨型通用 Task 表和大量 JSON 业务数据；
- 破坏明确的事实所有权。

### 4.4 默认采用事件溯源

否决原因：

- 当前没有足够团队经验和业务收益证明；
- 增加查询、迁移、调试和运维复杂度；
- 现阶段采用关系型主存储、只追加历史、领域事件和 Outbox 已能满足可追溯要求。

## 5. 影响

### 正向影响

- 文档、代码、数据库和接口可以共享稳定语言；
- WorkOrder 接单纵向切片可以继续实施，无需一级重命名；
- 后续聚合和模块 Review 有统一裁决标准；
- AI、规则和车企协议可以通过适配层扩展而不污染核心语言。

### 代价

- 需要逐步清理已有含糊名称，如 `Follower`、`Master`、`Attachment` 和无版本的 `current rule`；
- 新增模块必须补充领域语言和聚合目录；
- 已有代码与本 ADR 冲突时，需要通过独立重构 PR 修正。

## 6. 实施要求

1. 将 `domain/` 目录加入架构书阅读顺序；
2. 代码评审检查一级领域名称和聚合事实所有权；
3. 新增 OpenAPI、事件和数据库表时使用统一语言；
4. Spring Modulith 模块不得通过内部类或直接读表绕过上下文边界；
5. `WorkOrder` PR 合并前，必须验证其没有吞并 Task、Evidence、Review 和 Settlement 职责；
6. 后续 Task、Evidence、Review 和 Dispatch 实现必须引用本 ADR。

## 7. 重新评估触发条件

仅在出现以下证据时重新评估：

- 多行业产品化证明“工单”语言成为明确阻碍；
- 一个上游服务请求长期稳定地对应多个、可独立结算的履约实例，且现有 ServiceRequest + WorkOrder 模型无法表达；
- ReviewCase 无法支持已验证的新审核类型；
- 模块边界造成持续、可量化的事务或性能问题。

重新评估必须包含迁移范围、兼容策略、数据库和事件版本方案，不接受仅基于命名偏好的重构。
