---
title: 配置与执行内核逻辑数据模型
version: 0.1.0
status: Proposed
---

# 配置与执行内核逻辑数据模型

本文件定义逻辑实体与所有权，不是最终 DDL。物理表名、索引、分区和 JSONB 使用方式需结合查询、数据规模和技术栈评审。

## 1. 模块所有权

| 模块 | 拥有实体 | 其他模块访问方式 |
|---|---|---|
| 项目目录 | Client、Brand、ServiceProduct、Project、ProjectServiceProductBinding | 项目查询/命令 API |
| 配置版本 | Asset、DraftRevision、Validation/ReplayRun、ReleaseCandidate、Approval、PublishedVersion、Release、Bundle | 配置查询/命令 API |
| 工单 | WorkOrder、StageInstance、OwnerAssignment | 工单应用服务/事件 |
| 任务 | Task、TaskAssignment、TaskExecutionAttempt | 任务应用服务/事件 |
| 流程适配 | ProcessInstanceLink、CorrelationInbox | 仅适配器内部 |
| 可靠性运行时 | OutboxEvent、ConsumerInbox、CommandIdempotencyRecord、AsyncOperation、ScheduledExecution | 可靠消息/幂等/调度框架 API |
| 查询投影 | WorkOrderSummary、UserTodo、TimelineProjection | 只读查询 API |

## 2. 项目与服务目录

### client / brand

Client 保存签约/对接商业主体稳定身份、编码和状态；Brand 保存显示品牌、client 归属和有效区间。品牌不是项目，不在表中复制流程、价格或表单配置。

### service_product

保存 stableCode、名称、分类（勘测/安装/维修/拆装/组合）、业务定义、状态和替代关系。它是可复用业务产品目录，不等同某车企流程版本。

### project

| 字段 | 说明 |
|---|---|
| project_id / tenant_id | 身份和隔离 |
| client_id / project_code / name | 商业主体和稳定编码 |
| contract_period / business_window | 合同/运营时间范围 |
| status | DRAFT/ACTIVE/STOP_NEW_ORDERS/RETIRED |
| owner_refs | 品牌/项目责任引用 |
| default_region_scope_ref | 默认区域范围，不替代网点覆盖 |
| aggregate_version | 乐观锁 |
| created_at / activated_at / retired_at | 时间 |

### project_brand / project_region / project_network

使用带有效区间的关联记录表达项目适用品牌、区域和网点；变更保留历史，不在 project 中存可覆盖数组作为唯一事实源。

> M64 已落地 `prj_project_region` 的创建时写入与实时授权读取：复合外键约束 tenant/project 一致，
> `valid_from/valid_to` 决定当前关系，`regionCodes` 仅作为 API 输入/输出，不替代关系表事实。
> M65 以 `prj_project_network` 和 `networkIds` 落地同等约束；该关系不替代 ServiceNetwork 目录、
> Coverage、Capability 或停派事实。M66 以有效期结束/追加、开放关系唯一索引和不可变
> `prj_project_scope_revision` 收据实现即时整组修订。

### project_service_product_binding

保存 project、serviceProduct、适用 brand/region/桩型/车型等受控 scope、有效区间、状态和来源审批。`project + service_product + scope_business_key + effective_from` 唯一。

绑定只说明项目提供哪类服务；具体流程、表单、资料、SLA、派单和价格由受理时匹配到的履约方案版本（对应 ConfigurationRelease/Bundle）决定。

## 3. 配置实体

### configuration_asset

| 字段 | 说明 |
|---|---|
| asset_id | 全局标识 |
| tenant_id | 租户/业务隔离标识 |
| asset_type | 资产类型 |
| stable_code | 稳定业务编码，租户内唯一 |
| name | 名称 |
| owner_org_id / owner_user_id | 资产责任方 |
| risk_level | 普通/高风险 |
| created_at / created_by | 审计 |

### configuration_draft_revision

| 字段 | 说明 |
|---|---|
| revision_id / asset_id / revision_no | 修订身份 |
| base_version_id | 派生或升级基线 |
| content | 草稿内容，具体格式由资产 Schema 定义 |
| content_digest | 内容摘要 |
| change_reason | 修改原因 |
| status | DRAFT/VALIDATING/REVIEW_PENDING/... |
| created_at / created_by | 审计 |

### configuration_published_version

| 字段 | 说明 |
|---|---|
| version_id / asset_id | 版本身份 |
| semantic_version | 展示与治理版本号 |
| resolved_content | 发布前已解析的完整内容 |
| content_digest | 不可变校验摘要 |
| dependency_manifest | 精确依赖版本 |
| interpreter_min_version | 运行时兼容约束 |
| published_at / published_by / approved_by | 发布审计 |

发布版本业务内容、摘要和依赖不可更新。停止新引用不修改本实体。

### configuration_validation_run / configuration_replay_run

validation 保存 draft/candidate 精确 contentDigest、profile、Schema/引用/依赖/安全/覆盖检查、样本 refs、错误和解释器版本；replay 保存 sampleSet、baseline、逐样本结果、差异分类和 SideEffectFence 决定。内容 digest 改变后旧运行不能用于发布 Gate。

### configuration_release_candidate / candidate_item

candidate 保存拟发布范围、effectiveWindow、manifest/contentDigest、整组验证/回放/影响引用、状态（DRAFT/VALIDATING/REVIEW_PENDING/APPROVED/REJECTED/PUBLISHED）和 aggregateVersion；item 精确引用 approved draft revision/contentDigest。

### configuration_approval / approval_decision

保存 targetType/targetId/targetDigest、审批策略/步骤、提交人、决定人、决定、条件、MFA/职责分离证据和时间。审批绑定精确 digest；target 改变后状态投影为 STALE，不能用于 publish。

### configuration_release / configuration_release_item

`configuration_release` 保存原子发布身份、范围、业务日期口径、manifest 摘要和审批；item 保存资产版本清单。

### release_applicability / release_activation_event

`release_applicability` 保存归一化解析范围键、业务时间区间、灰度条件和对应 release。相同解析范围键的有效业务时间区间必须排他；发布时对范围键加事务级互斥锁并复查重叠。

`release_activation_event` 以追加记录表达激活、停止新绑定、替代和撤销错误激活。当前可绑定状态由事件投影计算，不更新发布版本内容。

### configuration_bundle / configuration_bundle_item

bundle 保存一次确定解析的上下文摘要、release 和 manifest 摘要；item 保存锁定的版本。对相同解析上下文和 manifest 可安全复用 bundle，但工单必须保存明确 bundle ID。

### fulfillment_plan / fulfillment_plan_version

`fulfillment_plan` 保存项目下履约方案身份、code、matchPriority、status（ENABLED/DISABLED/ARCHIVED）与 active/draft 版本指针；`fulfillment_plan_version` 保存版本状态（DRAFT/ACTIVE/HISTORICAL）、结构化适用范围快照与各配置快照，并对应一个 configuration_bundle。数据库唯一约束保证每个方案最多一个 DRAFT、一个 ACTIVE（见 DEC-007 / AD-014）。当前实现为 `cfg_project_fulfillment_profile`/`_revision`（按 serviceProductCode 键），结构化匹配与术语对齐为接受的目标。

## 4. 工单实体

### work_order

| 字段 | 说明 |
|---|---|
| work_order_id | 工单标识 |
| tenant_id | 隔离标识 |
| service_request_id | 原服务请求 |
| client_id / project_id / brand_id / service_product_id | 业务范围 |
| fulfillment_plan_id / fulfillment_plan_version_id | 受理时匹配并绑定的履约方案与方案版本 |
| match_mode / matched_at / matched_by | 匹配模式（AUTO/MANUAL/EXCEPTION/REMATCH）、时间与操作者 |
| configuration_bundle_id | 方案版本锁定的配置包 |
| authority_assignment_id / authority_version | 创建时锁定的唯一写入权威引用 |
| lifecycle_status | 稳定生命周期 |
| priority / risk_level | 调度和 SLA 输入 |
| current_stage_projection | 可重建展示投影 |
| aggregate_version | 乐观锁版本 |
| created_at / activated_at / fulfilled_at / closed_at | 时间 |

### work_order_external_ref

保存来源系统、外部工单号、外部项目和幂等键；`tenant + sourceSystem + externalOrderNo` 建议唯一。

### work_order_data_correction

保存 workOrder、fieldCode、旧值摘要、新类型化值/摘要、原因、证据、申请/审批、FieldPolicy/配置版本、影响分析引用、操作者和时间。更正记录只追加；工单基础字段当前投影由已接受更正链计算。

仅允许 workorder 模块拥有的字段。指向 FormSubmission、Appointment、ServiceAssignment、FulfillmentFact 等对象的更正请求必须拒绝并返回对应领域动作；不能通过该表复制其他模块的新值。

### stage_instance

保存阶段定义版本、业务键、状态、开始/完成时间和实例序号。返工或重开时可产生同一定义的新实例序号。

### owner_assignment

保存品牌负责人、项目经理、客服协调人等工单参与关系，包含角色类型、生效区间、来源策略和替代关系。

## 5. 任务实体

### task

| 字段 | 说明 |
|---|---|
| task_id / work_order_id / stage_instance_id | 身份和归属 |
| task_definition_version_id / task_code | 锁定定义 |
| task_type | HUMAN/AUTOMATED/EXTERNAL/TIMER/COORDINATION |
| status | 任务状态 |
| business_key | 防止同一流程位置重复建任务 |
| result_ref_type / result_ref_id / result_ref_version | 业务结果引用 |
| current_primary_sla_instance_id | 当前主要 SLA 查询投影，可空 |
| failure_policy_version_id | 失败策略 |
| aggregate_version | 乐观锁 |
| ready_at / claimed_at / started_at / completed_at | 时间 |

`work_order_id + business_key + instance_sequence` 应具有唯一约束。

### task_assignment

保存候选用户/角色/组织或实际责任人、生效区间、分配来源和解释。关键字段包括：

| 字段 | 说明 |
|---|---|
| task_assignment_id / task_id | 身份和任务 |
| assignment_kind | CANDIDATE/RESPONSIBLE |
| principal_type / principal_id | 用户、角色或组织 |
| status | PREPARED/ACTIVE/REVOKED/EXPIRED |
| source_type / source_id | 责任人策略、ServiceAssignment 等来源 |
| preparation_key | 师傅改派握手幂等键，可空 |
| effective_from / effective_to | 生效区间 |
| supersedes_task_assignment_id | 替代链 |

实际责任归属同一时刻最多一个 ACTIVE。`source_type=SERVICE_ASSIGNMENT + source_id` 唯一，重复握手事件返回已有 PREPARED/ACTIVE 结果。

### task_execution_guard

保存 Task 暂时不可执行的权威约束：

| 字段 | 说明 |
|---|---|
| guard_id / task_id | 身份和任务 |
| guard_type | REASSIGNMENT/SECURITY_HOLD/... |
| saga_id / source_ref | 来源握手/异常 |
| status | ACTIVE/RELEASED/CANCELLED |
| reason | 原因 |
| created_at / released_at | 时间 |

Task 的 claim/start/complete 等命令必须检查不存在阻断性 ACTIVE guard。`task_id + guard_type + saga_id` 唯一，guard 创建和 PREPARED TaskAssignment 在同一任务模块事务中完成。

### task_execution_attempt

每次自动执行保存 attemptNo、业务幂等键、claimOwner/claimUntil、开始/结束时间、结果、错误分类、外部调用引用和 nextRetryAt。业务重试时间只由任务模块拥有；外部 DeliveryAttempt 不再维护另一套退避时钟。

### task_block_record

保存阻塞原因、证据、操作者、开始/解除时间和是否影响 SLA。

### task_sla_link

保存 Task 与全部 SlaInstance 的关系、用途（PRIMARY/ESCALATION/CORRECTION 等）、生效区间和当前标记。Task 可以因整改轮次或不同考核目标关联多个 SLA；`current_primary_sla_instance_id` 只是可重建的快速引用。

## 6. 流程关联

### process_instance_link

保存业务工单/流程实例和引擎实例的映射、流程定义版本、启动时间和终止状态。业务模块不依赖引擎内部主键之外的表结构。

### correlation_inbox

保存待关联的业务事件、业务键、引擎处理结果和幂等状态，防止同一事件重复推进流程。

## 7. 可靠消息

### outbox_event

| 字段 | 说明 |
|---|---|
| event_id / outbox_id | 稳定事件和记录标识 |
| tenant_id / project_id / work_order_id / task_id | 数据范围与业务追踪 |
| aggregate_type / aggregate_id / aggregate_version | 来源聚合 |
| event_type / event_version | Schema 注册表契约 |
| payload_ref / payload_digest | 最小事件载荷或受控引用 |
| correlation_id / causation_id | 追踪 |
| partition_key | 需要聚合顺序时的稳定键 |
| status | PENDING/CLAIMED/PUBLISHED/FAILED/DEAD |
| available_at / claim_owner / claim_until | claim 与崩溃恢复 |
| publish_attempts / last_error_code | 投递诊断 |
| occurred_at / created_at / published_at | 时间 |

Worker 使用短事务和 `FOR UPDATE SKIP LOCKED`（或等价机制）claim。发布成功但 PUBLISHED 保存失败时允许按同一 eventId 重发，因此消费者必须幂等。

### consumer_inbox

以 `consumer_name + event_id` 唯一，保存 eventVersion、payloadDigest、状态（RECEIVED/PROCESSING/SUCCEEDED/FAILED_FINAL）、attempt、处理结果摘要和时间。业务处理结果、消费者自己的 Outbox 与 inbox SUCCEEDED 在同一数据库事务内提交。同 eventId 不同 digest 拒绝并进入安全/契约异常。

### command_idempotency_record

以 `tenant_id + command_scope + idempotency_key` 唯一，保存请求摘要、actor、命令类型、状态（IN_PROGRESS/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL）、聚合/operation ID、首次响应状态/摘要、开始/完成和过期时间。相同键且摘要相同返回首次结果；摘要不同返回幂等冲突。记录创建、领域命令首次业务写入和 SUCCEEDED 结果必须在同一事务边界内协调。

### scheduled_execution

保存 executionType、业务 sourceRef、priority、availableAt、状态（PENDING/CLAIMED/SUCCEEDED/RETRY_WAIT/FAILED_FINAL/CANCELLED）、claimOwner/claimUntil、attemptCount、关联 Task/TaskExecutionAttempt 和最后错误。`execution_type + source_business_key` 保证同一业务调度唯一。

Scheduler 只 claim 到期 execution；真正的重试政策、nextRetryAt 和人工接管仍由对应业务 Task 拥有。无 Task 的基础设施清理类 execution 必须有独立保留/失败策略，不能静默丢弃。

### async_operation

保存异步命令和流程初始化的可查询执行状态：

| 字段 | 说明 |
|---|---|
| operation_id / tenant_id | operation 身份 |
| command_id / idempotency_record_id | 来源命令与幂等关联 |
| operation_type | 取消补偿、配置迁移、流程初始化等 |
| target_resource_type / target_resource_id | 目标资源 |
| status | PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED |
| progress_summary | 非业务事实的进度摘要 |
| result_resource_version | 成功后的目标版本 |
| error_code / error_detail_ref / retryable | 失败信息 |
| created_at / started_at / finished_at | 时间 |

同一命令最多创建一个 operation；状态迁移使用乐观锁。operation 只是异步执行句柄，不能成为工单或任务状态的第二事实源。

## 8. 查询投影

### work_order_summary

包含列表展示所需的品牌、项目、区域、网点、师傅、生命周期、当前阶段、当前待办、SLA 风险和外部同步摘要。它由事件更新，可重建。

### user_todo

按用户/角色/组织展开可处理任务，保存任务版本、动作摘要、截止时间和数据范围摘要。真正执行命令时仍重新鉴权。

### work_order_timeline

保存面向用户的时间线条目，引用原领域事件、任务、审核或外部调用。不得以自由文本替代原始审计记录。

## 9. 关键约束

- 已发布配置版本不可 UPDATE 内容；
- 被 bundle 引用的版本不可物理删除；
- 工单受理绑定后 `fulfillment_plan_version_id` 与 `configuration_bundle_id` 默认不可修改（重匹配/调整走显式命令）；
- 状态变更只能通过应用服务命令；
- 事件与聚合写入使用同一事务；
- 资料、审核、派单和金额实体不塞入工单或任务表；
- 敏感字段按列/对象分类，投影默认脱敏；
- 审计记录和版本内容满足合同要求的保留期限。

## 10. 索引与规模验证清单

物理设计前使用真实数据验证：

- 月工单量、在途工单量和单工单任务数；
- 时间线、事件和操作日志增长；
- 用户待办和品牌/区域列表的主要筛选组合；
- 配置发布与运行时读取比例；
- Outbox 峰值、重试和积压；
- 历史查询和归档年限；
- 图片/视频仅保存元数据和对象存储引用，不入关系库大字段。
