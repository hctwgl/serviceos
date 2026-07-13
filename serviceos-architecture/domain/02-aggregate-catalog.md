---
title: ServiceOS 聚合目录与不变量
version: 0.1.0
status: Proposed
---

# ServiceOS 聚合目录与不变量

## 1. 目的

本文件定义核心聚合根、事实所有权、关键不变量和禁止承载的职责。它用于指导 Java 模块、Repository、事务边界、数据库表和领域测试。

## 2. 聚合设计原则

1. 聚合只维护必须强一致的不变量；
2. 聚合之间只引用稳定 ID 或不可变快照；
3. 一个命令默认只修改一个聚合；
4. 跨聚合副作用通过领域事件和 Outbox 完成；
5. Repository 面向聚合，不提供绕过聚合行为的字段级更新；
6. 历史、决定和版本事实优先追加，不原地覆盖；
7. 查询投影可以聚合多上下文数据，但不成为写入模型；
8. 不采用事件溯源作为默认主存储，领域事件用于集成、审计和投影。

## 3. 核心聚合目录

### 3.1 WorkOrder

**聚合根：** `WorkOrder`

**拥有：**

- `WorkOrderId`；
- `ExternalOrderReference`；
- `ProjectId`、`ServiceProductId`；
- `ConfigurationBundleReference`；
- 工单级客户、地址、车辆、设备接单快照；
- 工单级生命周期；
- 风险等级和工单级暂停/取消/关闭原因；
- 乐观锁版本。

**关键不变量：**

- 同一车企/客户域内外部订单业务键唯一；
- 创建时必须锁定已发布配置包的精确版本；
- 同一业务键的相同载荷可安全重放，不得创建第二工单；
- 同一业务键的冲突载荷或冲突配置版本必须拒绝并转人工；
- 已取消或关闭工单不能隐式恢复；
- 履约完成与最终关闭必须区分；
- 恢复、重开和强制关闭必须记录原因、操作者和事件。

**不拥有：** Task、EvidenceRevision、ReviewDecision、DispatchDecision、Appointment 历史、CalculationRun 和 SettlementStatement。

**Repository：** `WorkOrderRepository`

**典型命令：**

- `ReceiveExternalWorkOrder`；
- `SuspendWorkOrder`；
- `ResumeWorkOrder`；
- `CancelWorkOrder`；
- `MarkWorkOrderFulfilled`；
- `CloseWorkOrder`；
- `ForceCloseWorkOrder`。

**典型事件：**

- `WorkOrderReceived`；
- `WorkOrderSuspended`；
- `WorkOrderResumed`；
- `WorkOrderCancelled`；
- `WorkOrderFulfilled`；
- `WorkOrderClosed`；
- `WorkOrderForceClosed`。

### 3.2 WorkflowInstance

**聚合根：** `WorkflowInstance`

**拥有：**

- 流程定义精确版本；
- 所属 WorkOrderId；
- 当前运行状态；
- StageInstance 标识和任务图推进游标；
- 已处理事件/命令幂等标识；
- 完成、取消和失败原因。

**关键不变量：**

- 一个流程实例永远绑定一个流程定义版本；
- 已完成节点不得因配置升级被重新解释；
- 路由必须基于冻结的输入或明确的运行时事实；
- 相同完成事件不得重复创建后续任务；
- 并行分支汇聚必须满足定义中的完整条件。

**不拥有：** 任务责任人、证据、审核决定和派单候选明细。

### 3.3 Task

**聚合根：** `Task`

**拥有：**

- TaskId、类型和所属 StageInstanceId；
- 责任人策略解析后的候选人与执行人；
- 状态和允许动作；
- 输入/输出业务对象引用；
- SLA 引用；
- 领取、开始、阻塞、重试、完成、取消和人工接管历史摘要；
- 乐观锁或租约信息。

**关键不变量：**

- 未 READY 的任务不能被领取；
- 同一时刻只能有一个有效执行租约；
- 完成必须满足任务定义的完成条件；
- 完成命令必须幂等；
- 自动重试耗尽后必须进入人工接管，不能无限重试；
- 取消任务必须有上游业务原因，不能删除历史。

**不拥有：** ReviewDecision、EvidenceRevision、DispatchDecision 的内容。

### 3.4 DispatchRequest

**聚合根：** `DispatchRequest`

**拥有：**

- 派单目标、范围和策略版本；
- DispatchAttempt；
- DispatchDecision；
- CandidateEvaluation 快照；
- 容量预占引用；
- 最终选择或失败原因。

**关键不变量：**

- 硬过滤未通过的候选不能进入评分；
- 每次决定必须保留输入快照、评分和解释；
- 只有一个成功决定可以激活有效 ServiceAssignment；
- 容量预占与激活必须避免并发超卖；
- 人工覆盖必须记录权限、原因和被覆盖的自动决定；
- 改派必须终止旧 Assignment 的有效性，不能覆盖历史。

### 3.5 ServiceAssignment

**聚合根：** `ServiceAssignment`

**拥有：**

- 被服务对象 TaskId；
- ServiceNetworkId、TechnicianId；
- 分配来源和 DispatchDecisionId；
- 生效、撤销、改派和完成状态；
- 责任生效时间范围。

**关键不变量：**

- 一个分配在同一责任层级只有一个当前有效版本；
- 被撤销分配不能重新激活，只能创建新分配；
- 改派后原网点或师傅的业务操作权限立即失效，但审计可见性保留。

### 3.6 Appointment

**聚合根：** `Appointment`

**拥有：**

- 预约对象和时间窗口；
- 联系方式快照；
- 发起人、确认人和渠道；
- 改约、取消、用户未响应和确认历史。

**关键不变量：**

- 时间窗口必须有效；
- 改约创建新版本或显式变更历史；
- 已取消预约不可再次确认；
- 预约完成不等于上门完成。

### 3.7 Visit

**聚合根：** `Visit`

**拥有：**

- 对应 TaskId 和 AppointmentId；
- 到达、离开和现场位置；
- 上门结论；
- 空跑、无法施工、二次及多次上门原因；
- 现场执行人。

**关键不变量：**

- 每次实际上门独立记录；
- 空跑和无法施工必须有标准原因与必要证明；
- 现场时间、定位和执行人不可被普通角色原地修改。

### 3.8 EvidenceItem

**聚合根：** `EvidenceItem`

**拥有：**

- EvidenceSlotId；
- 逻辑资料项身份；
- EvidenceRevision 列表；
- 当前待审版本和已接受版本引用；
- 采集约束结果摘要。

**关键不变量：**

- 新上传永远创建新 Revision；
- Revision 一经提交不可修改文件引用和采集元数据；
- 审核决定必须指向明确 Revision；
- 已接受版本不能被普通角色覆盖；
- 相同文件重复提交必须可识别，但是否拒绝由项目规则决定。

### 3.9 EvidenceSetSnapshot

**聚合根：** `EvidenceSetSnapshot`

**拥有：**

- 某次提审时冻结的 EvidenceRevisionId 集合；
- 对应资料模板版本；
- 创建原因、创建人和时间；
- 内容摘要。

**关键不变量：**

- 快照创建后不可增删成员；
- 审核、回传和计价引用快照而不是“当前资料”；
- 相同内容可以幂等复用，但不得悄然指向新版本。

### 3.10 ReviewCase

**聚合根：** `ReviewCase`

**拥有：**

- 审核类型和来源；
- 不可变审核对象引用；
- ReviewDecision 历史；
- 标准驳回原因与补充说明；
- 强制通过授权依据；
- CorrectionCase 引用；
- 当前审核结论投影。

**关键不变量：**

- 决定只追加，不覆盖；
- 驳回必须指向明确对象和版本；
- 强制通过必须与普通通过区分；
- 车企决定和总部决定来源必须可区分；
- 已通过后的重新驳回必须追加新决定或重开案例；
- 审核人不能通过 ReviewCase 绕过 Evidence 权限修改资料。

### 3.11 CorrectionCase

**聚合根：** `CorrectionCase`

**拥有：**

- 触发决定或异常；
- 整改范围、责任人和截止时间；
- 补传资料或修正表单引用；
- 提交、复核、完成和关闭历史。

**关键不变量：**

- 每个整改案例必须有明确触发源；
- 只能处理声明的整改范围；
- 完成整改不自动等于审核通过；
- 多轮整改分别保留历史。

### 3.12 SlaInstance

**聚合根：** `SlaInstance`

**拥有：**

- SLA 策略和日历精确版本；
- 开始、截止、剩余时长；
- 暂停/恢复区间；
- 预警、超时和升级记录。

**关键不变量：**

- 暂停和恢复必须成对且不可重叠；
- 计时结果可由历史事件重算；
- 同一预警阈值只触发一次；
- 超时事实不能因后续完成而删除。

### 3.13 FulfillmentFact

**聚合根：** `FulfillmentFact`

**拥有：**

- 标准事实代码和值；
- 来源对象和版本；
- 提取规则版本；
- 可计价资格；
- 撤销或替代链路。

**关键不变量：**

- 每个事实必须可追溯到不可变来源；
- 修正事实通过新增或替代，不原地覆盖；
- 对上和对下不得各自维护冲突的履约事实；
- 车企驳回影响可计价资格时必须留下显式事实。

### 3.14 CalculationRun

**聚合根：** `CalculationRun`

**拥有：**

- FactSetSnapshot；
- PricingContextSnapshot；
- 计价方案精确版本；
- 对上或对下方向；
- ChargeItem；
- 公式解释、舍入和异常结果。

**关键不变量：**

- 一次运行输入不可变；
- 对上、对下运行完全分离；
- 结果必须可解释和重放；
- 新规则只产生新运行，不覆盖旧金额。

### 3.15 SettlementStatement

**聚合根：** `SettlementStatement`

**拥有：**

- 结算主体、方向和期间；
- StatementLine；
- 对账差异、争议和 Adjustment；
- 审批、确认、锁定、支付/收款状态。

**关键不变量：**

- 已锁定单据不得原地改金额；
- 调整必须使用独立 Adjustment；
- 每条结算行必须引用可追溯 ChargeItem 或调整依据；
- 对上和对下单据分离；
- 正式结算启用前必须通过影子试算与对账 Gate。

## 4. 技术事实聚合

以下属于支撑性上下文，不应混入业务聚合：

- `InboxMessage`；
- `OutboxMessage`；
- `IdempotencyRecord`；
- `StoredFile`；
- `AuditRecord`；
- `NotificationDelivery`；
- `OperationalException`。

它们可以与业务标识关联，但不拥有业务生命周期。

## 5. Repository 规则

Repository 可以提供：

- `findById`；
- 按业务唯一键查找聚合；
- `save`；
- 必要的并发锁定读取；
- 领域专用存在性查询。

Repository 禁止提供：

- `updateStatus`；
- `updateNetworkId`；
- `approveDirectly`；
- 绕过聚合不变量的批量字段更新；
- 跨上下文 Join 后直接持久化业务结果。

批量运营操作必须拆解为可审计命令，或通过专门批处理应用服务逐个执行聚合规则。

## 6. 事务边界

默认事务模板：

```text
加载一个聚合
→ 执行一个业务命令
→ 保存聚合
→ 写审计意图/记录
→ 写 Outbox 事件
→ 同一数据库事务提交
```

外部 API、消息代理、对象存储、短信和车企接口调用不得放在数据库事务内。

跨聚合业务流程使用 Saga/Process Manager、Workflow 或事件编排，不通过扩大单事务解决。

## 7. 必须具备的测试类型

每个聚合至少需要：

1. 首次成功命令测试；
2. 相同命令幂等重放测试；
3. 冲突命令拒绝测试；
4. 非法状态迁移测试；
5. 乐观并发或容量竞争测试；
6. 历史不可变测试；
7. Repository PostgreSQL 集成测试；
8. 事务回滚时 Outbox/审计一致性测试。
