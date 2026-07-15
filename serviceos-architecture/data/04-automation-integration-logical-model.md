---
title: 派单、SLA、集成、通知与异常逻辑数据模型
version: 0.1.0
status: Proposed
---

# 派单、SLA、集成、通知与异常逻辑数据模型

> M56～M57 以 V055～V057 落地 BYD 创建工单/审核回调的 Envelope、Canonical 和路由数据。
> M58 以 V058 落地 BYD 提审所需的 OutboundDelivery、DeliveryAttempt 和
> ExternalAcknowledgement 最小子集。ReplayRequest、通用 Connector 和本文其余模型仍为 Proposed。
> M62 只为 M61 `sla_instance` 增加 project/workOrder 稳定游标索引和 `sla.read` capability；没有新增
> SLA 业务状态、默认 deadline、日历或暂停数据结构。
> M65 的 `prj_project_network` 由项目目录拥有，只为 NETWORK RoleGrant 提供项目范围；它不是本章
> `service_network`、Coverage、Capability 或停派模型的替代实现。

本文件定义 M4 逻辑实体和关键约束，不是最终 DDL。

## 1. 模块所有权

| 模块 | 拥有实体 | 不拥有 |
|---|---|---|
| 服务网络 | ServiceNetwork、Coverage、Capability、TechnicianProfile、NetworkTechnicianMembership、Qualification | WorkOrder、登录用户身份 |
| 派单 | DispatchRequest、Attempt、Decision、Evaluation、ServiceAssignment、CapacityReservation | TaskAssignment 和 SLA |
| SLA | SlaInstance、ClockSegment、Pause、MilestoneTrigger、Recalculation | Task 状态和处罚金额 |
| 集成 | ConnectorVersion、InboundEnvelope、OutboundDelivery、Attempt、Acknowledgement | 领域对象状态 |
| 通知 | Plan/Template Version、Intent、Delivery、Attempt、Receipt | 工单/预约事实 |
| 异常 | OperationalException、Occurrence、Resolution | handling Task 的责任和 SLA |

## 2. 服务网络

### service_network

保存网点身份、组织引用、状态、合同有效期、结算主体和聚合版本。启用、停派、清退分别建模；停派可以有范围和时间，不能只用一个布尔值解释所有情况。

### network_status_period

保存状态类型、适用项目/业务范围、生效区间、原因、申请/审批和解除记录。

### service_coverage_version / coverage_item

保存品牌、项目、业务类型、行政区域、可选地理范围、偏远授权和版本摘要。覆盖版本发布后不可变。

### service_capability_version / capability_item

保存业务产品、桩型/设备品牌、技能、人员数量和资质要求。

### technician_profile

保存 technicianId、principal/person 引用、显示名、服务状态（ACTIVE/SUSPENDED/RETIRED）、联系方式引用、能力摘要投影和聚合版本。登录凭据、密码和会话不属于本表。

### network_technician_membership

保存 network、technician、关系类型、有效区间、状态（PENDING/ACTIVE/SUSPENDED/ENDED）、来源/审批和结束原因。`network + technician + validity_business_key` 唯一；派单引用精确 membershipId/version。

停用/结束前必须评估未完成 Task、Appointment 和 ServiceAssignment，并产生重新分配计划；不能只把状态改为 ENDED。

### qualification

保存 ownerType（NETWORK/TECHNICIAN）/ownerId、资质类型、编号摘要、签发机构、有效期、状态（PENDING_VERIFICATION/VERIFIED/REJECTED/EXPIRED/REVOKED）、验证策略/决定和附件资料引用。敏感证书按字段/资料权限保护；上传材料本身不自动成为 VERIFIED。

### network_metric_snapshot

保存 metricCode、统计范围/周期、值、样本量、质量标记、截止时间、算法版本和生成时间。

## 3. 派单

### dispatch_request

| 字段 | 说明 |
|---|---|
| request_id / work_order_id / task_id | 身份与业务关联 |
| target_type | NETWORK/TECHNICIAN |
| policy_version_id | 锁定策略 |
| input_context_digest | 标准输入摘要 |
| status | PENDING/RUNNING/COMPLETED/FAILED/CANCELLED |
| failure_code | 失败分类 |
| requested_at | 时间 |

`failure_code` 是当前查询投影；完整失败历史保存在 DispatchAttempt。

### dispatch_attempt

保存 request、attemptNo、taskExecutionAttemptId、输入摘要、开始/结束、结果、失败分类、decisionId 和 correlationId。`request_id + attempt_no` 唯一。它不保存 nextRetryAt，也不负责调度。

### dispatch_decision

保存 request、决策序号、策略版本、输入快照、选择模式、选中候选、选择人、原因、审批和摘要。一次 request 重算产生新 decision，不覆盖旧结果。

### candidate_evaluation / candidate_rule_result / candidate_score_component

分别保存候选总结果、每条硬过滤结果和每个评分指标。候选指标使用 snapshot ID，不只保存最终分。

### service_assignment

保存层级（NETWORK/TECHNICIAN）、assignee、生效区间、来源 decision、状态（PENDING_ACTIVATION/ACTIVE/ENDED/FAILED_ACTIVATION）、被替代 service assignment、preparedTaskAssignmentRef、改派原因，以及激活时的 fenceDecisionId、authorityAssignmentId、authorityVersion 和 fencePolicyVersion。同工单/任务/层级同一时刻最多一个 ACTIVE；激活事务必须再次校验 authorityVersion。

### service_assignment_activation_saga

保存 sagaId、旧/新 ServiceAssignment、Task、阶段（PENDING/TASK_PREPARED/SERVICE_SWITCHED/TASK_ACTIVATED/COMPLETED/ABORTING/ABORTED）、preparedTaskAssignmentRef、guardRef、attempts、超时、补偿审批和最后错误。`new_service_assignment_id` 唯一。

### capacity_policy_version / capacity_counter / capacity_reservation

- policy 定义业务类型和周期上限；
- counter 保存当前权威占用与版本；
- reservation 关联 service assignment、占用单位、状态和释放原因。

容量预占和 ServiceAssignment 激活在同一模块事务内完成；不能只读报表统计后写 service_assignment。

### dispatch_policy_adjustment

保存策略/网点/业务范围、原值、新值、生效区间、原因、申请/审批、状态和到期恢复事件。

## 4. SLA

> M61 物理实现仅包含 Task ELAPSED 子集：`sla_instance`、单一 RUNNING `sla_clock_segment` 与
> TARGET_DUE `sla_milestone`。以下业务日历、暂停、升级和重算对象仍是逻辑规划。

### business_calendar_version / calendar_interval / calendar_exception

保存时区、周工作时段、节假日、调休和特殊停工日。版本内容不可变。

### sla_policy_version / sla_policy_milestone

保存 subject 类型、开始/停止事件、时长类型、目标、日历、暂停策略和预警/升级里程碑。

### sla_instance

| 字段 | 说明 |
|---|---|
| sla_instance_id / subject_type / subject_id | 身份和对象 |
| policy_version_id / calendar_version_id | 锁定配置 |
| status | PENDING/RUNNING/PAUSED/MET/BREACHED/MET_LATE/CANCELLED |
| started_at / deadline_at / stopped_at | 权威时间 |
| elapsed_business_duration / paused_duration | 当前投影 |
| next_milestone_at | 调度投影 |
| aggregate_version | 并发控制 |

### sla_clock_segment

保存 RUNNING/PAUSED 片段、开始/结束、原因和来源事件。一个实例片段不得重叠。

### sla_pause_record

保存策略原因、说明、证据、操作者、审批（如需）、开始/恢复和关联 segments。

### sla_milestone_trigger

以 `sla_instance_id + milestone_code + occurrence` 唯一，保存计划时间、实际触发时间、状态和产生的通知/升级引用。

### sla_escalation

保存 milestone trigger、严重度、收件人解析、通知意图、触发时间和可选 OperationalException 引用。它不拥有处理责任人、状态或处理 SLA。

### sla_recalculation

保存输入修正、旧/新 deadline 和结果、原因、审批、算法版本及当前认可标记。历史 milestone 不删除。

## 5. 集成

### connector_definition_version

保存渠道类型、端点引用、认证方式、速率、超时、能力、凭据引用和健康策略；不保存明文密钥。

### inbound_envelope / canonical_message

保存原始 payload 引用/摘要、运输元数据、外部键、去重键、验签、映射版本、处理状态和标准消息引用。

### outbound_delivery

保存连接器/映射版本、消息类型、业务键、源对象精确版本、payload 快照、外部幂等键、失败策略、executionTaskId、fenceDecisionId、authorityAssignmentId、authorityVersion、fencePolicyVersion 和状态。业务重试由该 Task 唯一调度；真正发送前 authorityVersion 变化必须重新判定，外部幂等键包含该版本。

### delivery_attempt

保存 attemptNo、taskExecutionAttemptId、请求/响应摘要和引用、外部流水、开始/结束、错误分类、连接器凭据版本。它不保存 nextRetryAt。

`delivery_id + attempt_no` 唯一；所有 attempt 只追加。

### external_acknowledgement

保存 delivery、外部回执 ID、技术/业务类型、结果、原因、正文引用、映射版本和受影响对象版本。

### replay_request

保存原 delivery、重放模式、申请/审批、预演结果、执行状态和新 delivery 引用。

## 6. 通知

### message_template_version / notification_plan_version

保存模板正文/供应商模板引用、变量 Schema、渠道、语言，以及事件、条件、收件人策略、去重和失败策略。发布版本不可变。

### notification_intent

保存 source event、plan version、业务 purpose、去重键、变量快照摘要、状态和 correlationId。

### recipient_resolution / resolved_recipient

保存参与关系/角色策略版本、解析输入、最终主体和渠道地址引用。地址原值使用受控联系信息服务，不在普通查询中返回。

### notification_delivery / notification_attempt / delivery_receipt

delivery 以 intent + recipient + channel 唯一并保存 executionTaskId、fenceDecisionId、authorityAssignmentId、authorityVersion 和 fencePolicyVersion；attempt 关联 TaskExecutionAttempt 且只追加；receipt 保存供应商送达/阅读事实。通知域不自行调度业务重试，真正发送前必须重新校验 authorityVersion。

## 7. 运营异常

### operational_exception

| 字段 | 说明 |
|---|---|
| exception_id / type / severity | 身份和分类 |
| source_type / source_id / source_version | 精确来源 |
| work_order_id / task_id / project_id | 业务范围 |
| deduplication_key | 聚合重复检测 |
| status | OPEN/ACKNOWLEDGED/IN_PROGRESS/RESOLVED/CLOSED/SUPPRESSED |
| resolution_code / resolved_at | 处理结果 |
| handling_task_ids | 任务引用，不保存 assignee |
| aggregate_version | 并发控制 |

### exception_occurrence

每次检测保存时间、错误摘要、attempt/source 引用和 correlationId。即使去重到同一 exception 也不丢发生次数。

### exception_resolution

保存解决动作、领域命令/重放引用、证据、操作者、结果和验证事件。状态转换只引用有效 resolution。

## 8. 关键约束

- DispatchDecision、CandidateEvaluation 和 DeliveryAttempt 只追加；
- 硬过滤结果和评分组成均可追溯到策略/指标版本；
- ServiceAssignment 与容量预占保持一致；
- 可执行师傅 Task 的 ACTIVE ServiceAssignment 与当前 TaskAssignment 一致；同步窗口必须有不可执行 guard；
- 激活 saga 的每个状态转换幂等；ABORTED 时不存在遗留容量预占和 PREPARED TaskAssignment；
- SLA segment 不重叠，milestone 幂等；
- OutboundDelivery payload 创建后不可变；
- 派单、外部交付和通知的业务重试时间只由 TaskExecutionAttempt 拥有；
- 技术送达、业务确认和业务拒绝分离；
- NotificationIntent 去重不丢关键升级；
- OperationalException 不拥有责任人和 SLA；
- 人工恢复必须引用真实领域命令/任务结果；
- 报文、联系方式、资质和指标中的敏感数据执行权限与审计。

## 9. 查询与规模验证

- 区域/品牌/业务类型的候选网点查询；
- 高并发容量预占竞争；
- 月度比例和指标快照批量读取；
- nextMilestoneAt 调度和漏触发 reconciliation；
- connector + delivery 状态 + 关联 TaskExecutionAttempt.nextRetryAt 的积压查询；
- exception 按项目/类型/严重度/SLA 的工作台查询；
- 通知高峰、频控、回执和保留期限；
- 原始报文和 attempt 日志归档策略。
