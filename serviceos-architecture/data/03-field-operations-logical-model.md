---
title: 现场作业、表单、资料与审核逻辑数据模型
version: 0.1.0
status: Proposed
---

# 现场作业、表单、资料与审核逻辑数据模型

本文件定义 M3 逻辑实体和模块所有权，不是最终 DDL。大文件存储在对象存储，关系库只保存元数据、摘要和业务关系。

## 1. 模块所有权

| 模块 | 拥有实体 | 不拥有 |
|---|---|---|
| 预约 | ContactAttempt、Appointment、AppointmentRevision | Task、通知模板 |
| 现场作业 | Visit、FieldOperation、FieldOperationSubmission | 动态表单定义、资料文件 |
| 表单字段 | FieldDefinition、FormVersion、FormDraft、FormSubmission | Customer/Vehicle 核心事实 |
| 资料 | EvidenceSlot、EvidenceItem、EvidenceRevision、EvidenceValidation | ReviewDecision |
| 审核整改 | ReviewCase、ReviewDecision、CorrectionCase、ExternalReviewReceipt | Task 责任人和 SLA |
| 移动同步 | MobileWorkPackage、OfflineCommandReceipt、SyncConflict | 领域对象权威状态 |

## 2. 预约实体

### contact_attempt

| 字段 | 说明 |
|---|---|
| contact_attempt_id / work_order_id / task_id | 身份与归属 |
| channel / contacted_party_ref | 渠道与联系对象 |
| started_at / ended_at | 联系时间 |
| result_code / note | 结果和说明 |
| next_contact_at | 下次联系计划 |
| actor_id / organization_id | 实际操作者 |
| recording_ref | 合规录音引用，可空 |

### appointment

| 字段 | 说明 |
|---|---|
| appointment_id / work_order_id / task_id | 身份和任务关联 |
| appointment_type | SURVEY/INSTALLATION/REPAIR/CORRECTION/... |
| status | 当前聚合状态 |
| current_revision_id | 当前有效修订投影 |
| assigned_network_id / technician_id | 当前服务资源快照 |
| aggregate_version | 乐观锁 |
| created_at / created_by | 审计 |

### appointment_revision

保存修订序号、地址引用/坐标快照、时间窗口、预计时长、时区、发起人、确认方、渠道、原因、配置版本和前一修订。修订内容不可更新。

`appointment_id + revision_no` 唯一。

### appointment_status_history

保存每次状态迁移、命令、操作者、原因和关联 revision。Appointment 当前状态是聚合字段，history 用于审计和时间线。

## 3. Visit 与现场操作

### visit

| 字段 | 说明 |
|---|---|
| visit_id / appointment_id / task_id | 身份和关联 |
| visit_sequence | 同预约内访问序号 |
| technician_id / network_id | 到场资源 |
| status | CHECKED_IN/COMPLETED/INTERRUPTED |
| captured/received timestamps | 到场离场采集与接收时间 |
| location refs / accuracy / geofence result | 定位证据 |
| device_id / offline_flag | 设备与离线标识 |
| result_code / exception_code | 结果 |
| aggregate_version | 并发控制 |

### field_operation

保存操作类型、关联 visit/task、状态、开始/结束时间、当前 submission、结果和异常。一次 visit 可有多个 operation，但 `task_id + operation_type + sequence` 唯一。

### field_operation_submission

保存不可变 submission 版本、表单提交引用、资料集合引用、提交人、提交时间、结果编码、异常编码、被替代版本和原因。

## 4. 字段与表单实体

### field_definition

保存稳定编码、业务对象、类型、单位、精度、枚举、敏感级别、用途标记、状态和替代字段。`tenant/shared namespace + field_code` 唯一。

### field_mapping_version / field_mapping_item

保存外部系统版本、外部路径、标准字段、转换、默认值和枚举映射。属于集成配置资产。

### form_definition / form_version

definition 保存稳定资产身份；version 保存已发布 data schema、UI schema、规则、依赖清单、摘要和解释器版本。内容不可更新。

### form_field_binding

逻辑上属于 form version，保存字段编码、位置、标签、预填、权限、验证和领域映射。物理实现可内嵌于版本文档或拆表，但发布摘要必须覆盖完整内容。

### form_draft

| 字段 | 说明 |
|---|---|
| draft_id / task_id / form_version_id / user_id | 唯一草稿范围 |
| values_document | 已校验类型的草稿值 |
| prefill_snapshot_version | 预填基线 |
| local_revision / server_revision | 同步版本 |
| updated_at / expires_at | 时间 |

草稿可覆盖，不作为业务事实。

### form_submission

| 字段 | 说明 |
|---|---|
| submission_id / task_id / form_version_id | 身份和锁定版本 |
| submission_version | 同一业务提交序号 |
| values_document | 不可变值文档 |
| prefill_snapshot_ref | 预填来源 |
| content_digest | 完整性摘要 |
| validation_status | SUBMITTED/VALIDATED/INVALID |
| submitted_by / submitted_at | 审计 |
| supersedes_submission_id / correction_reason | 更正链 |

### submission_validation

保存规则版本、输入摘要、结果、字段错误、警告和执行时间。服务端验证结果为权威。

## 5. 资料实体

### evidence_slot

| 字段 | 说明 |
|---|---|
| slot_id / task_id | 运行时资料槽位 |
| requirement_version_id / requirement_code | 锁定要求 |
| occurrence_key | 重复槽位稳定键 |
| condition_input_digest / explanation | 条件解析依据 |
| status_projection | MISSING/PARTIAL/SATISFIED/INVALIDATED |
| min_count / max_count | 数量约束快照 |

`status_projection` 可从 item、validation 和 review 重建，不作为审核事实源。

### evidence_item

保存逻辑资料 ID、slot、序号、当前 revision 投影和创建人。`slot_id + item_sequence` 唯一。

### evidence_revision

| 字段 | 说明 |
|---|---|
| revision_id / evidence_item_id / revision_no | 版本身份 |
| object_key / storage_provider | 对象存储引用 |
| checksum / size / mime_type | 完整性和格式 |
| lifecycle_status | STORED/VALIDATING/VALIDATED/VALIDATION_FAILED/QUARANTINED/INVALIDATED |
| capture_metadata_id | 采集元数据 |
| upload_session_id | 上传会话 |
| supersedes_revision_id | 补传链 |
| created_by / created_at | 审计 |

`evidence_item_id + revision_no` 和 checksum 相关幂等键建立唯一约束。对象 key 不作为外部 API 永久地址。

UploadSession 负责 `CREATED/UPLOADING/FINALIZING/COMPLETED/EXPIRED/FAILED` 上传状态；Finalize 成功并校验对象后才创建状态为 `STORED` 的 EvidenceRevision。

### capture_metadata

保存来源、采集/接收时间、坐标、精度、地址快照、设备、应用版本、水印策略摘要、EXIF 摘要、离线和代办信息。

### evidence_validation

一项 revision 可有多个校验结果，按 `validation_type + policy/model version` 区分。保存结果、分值、置信度、错误编码、输出摘要和执行时间。

### evidence_set_snapshot / evidence_set_member

snapshot 保存任务、用途（作业提交/审核/报告/回传）、创建时间、成员数、集合摘要和版本；member 精确引用 slot、item、revision、validation 摘要。创建后成员不可变，后续补传创建新 snapshot。

### upload_session

保存允许路径、类型、大小、分片、过期时间、任务/slot 权限、预期 checksum、状态和 finalize 幂等键。

## 6. 审核与整改实体

### review_case

| 字段 | 说明 |
|---|---|
| review_case_id / work_order_id / task_id | 身份和任务外壳 |
| review_origin / review_type | INTERNAL/CLIENT；MATERIAL/FORM/OPERATION/REINSPECTION/... |
| source_review_case_id | CLIENT Case 必须引用已通过的 INTERNAL Case |
| external_submission_ref / callback_batch_ref / mapping_version_id | 适配层确认外部提交后冻结的提交、回执批次和映射版本 |
| scope_type / scope_ref / scope_version | 精确审核对象 |
| policy_version_id | 审核策略 |
| status | OPEN/DECIDED/CORRECTION_PENDING/CLOSED |
| current_decision_id | 当前决定投影 |
| aggregate_version | 并发控制 |

### review_decision

保存决定序号、对象版本、类型、原因编码、说明、审核人、决定时间、强制授权/审批、机器校验摘要和被撤销决定。决定只追加。

### review_target_decision

当整组审核包含多个资料项时，保存每个 target 的精确 revision 和单项决定，支持单项驳回。

### correction_case

保存来源审核、状态、整改 Task 引用和当前轮次。责任人、候选人、截止时间和 SLA 只由关联 Task 拥有。

### correction_item

保存被驳回 target、原因、要求动作、当前补传/表单版本和验证结果。每轮整改保留历史。

### external_review_receipt

保存关联 ReviewCase、车企审核业务键、回传批次、结果、原始回执引用、映射版本、幂等键、原因映射和受影响对象的精确版本。适配成功后生成关联 ReviewDecision，不创建第二种审核 Case。

## 7. 移动同步实体

### mobile_work_package

保存任务、配置包、表单/资料版本摘要、预填版本、允许离线动作、签发人、设备、签发/过期时间和包摘要。不复制未授权的完整工单数据。

### offline_command_receipt

以 `tenant_id + device_id + device_command_id` 唯一，保存命令摘要、采集时间、接收时间、基础聚合版本、处理状态、权威结果和错误。

### sync_conflict

保存对象、客户端基线、服务器当前版本、冲突字段/资料、建议动作、处理人和解决结果。冲突解决必须转化为正式领域命令。

## 8. 关键约束

- AppointmentRevision、FormSubmission、EvidenceRevision、ReviewDecision 只追加；
- Task 是责任人和 SLA 唯一事实源；
- EvidenceRevision 不保存审核通过/驳回状态；
- ReviewDecision 精确引用被审核版本；
- 同一设备离线命令幂等；
- 完成 FieldOperation 必须引用已验证表单和满足条件的资料集合；
- 外部审核回调不得直接更新 Task；
- 表单 JSON 中的核心值通过命令映射到领域对象；
- 对象存储删除、隔离和派生文件均可追溯；
- 位置、图片和用户信息执行敏感数据策略。

## 9. 主要索引与规模验证

- task + appointment type + status；
- technician + appointment window；
- work order + visit sequence；
- task + form version + submission version；
- task + evidence requirement + slot status projection；
- review task + review status + due time；
- checksum/感知哈希的受控重复检测索引；
- upload session 过期清理；
- 离线 command receipt 的设备与时间范围；
- 资料、校验结果和审核决定的增长与归档。
