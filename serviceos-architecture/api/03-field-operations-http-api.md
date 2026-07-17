---
title: 预约、现场作业、表单、资料与审核 HTTP API
version: 0.1.0
status: Proposed
---

# 预约、现场作业、表单、资料与审核 HTTP API

沿用 [工单与任务 HTTP API](02-work-order-task-http-api.md) 的认证、幂等、`If-Match`、错误模型和 correlation 约定。

## 1. 联系与预约

| 方法与路径 | 命令/用途 | 必需载荷 | 成功 |
|---|---|---|---|
| `POST /tasks/{taskId}/contact-attempts` | RecordContactAttempt | channel、partyRef、resultCode、timestamps | 201 |
| `GET /tasks/{taskId}/contact-attempts` | 查询不可变联系历史 | — | 200 |
| `POST /tasks/{taskId}/appointments` | ProposeAppointment | type、window、addressRef、participants | 201 |
| `POST /appointments/{id}:confirm` | ConfirmAppointment | revision、confirmedByParty、channel | 200 |
| `POST /appointments/{id}:reschedule` | RescheduleAppointment | newWindow、reasonCode、note? | 200 |
| `POST /appointments/{id}:cancel` | CancelAppointment | reasonCode、note? | 200 |
| `POST /appointments/{id}:mark-no-show` | MarkNoShow | party、reason、evidenceRefs? | 200 |
| `GET /tasks/{taskId}/appointments` | 查询预约历史 | — | 200 |
| `GET /appointments/{id}` | 当前投影及全部修订链接 | — | 200 |

预约窗口：

```json
{
  "start": "2026-07-15T09:00:00+08:00",
  "end": "2026-07-15T12:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "estimatedDurationMinutes": 120
}
```

确认和改约必须携带 Appointment ETag，避免客服、网点和师傅相互覆盖。

## 2. Visit 与现场操作

| 方法与路径 | 命令/用途 | 必需载荷 | 成功 |
|---|---|---|---|
| `POST /appointments/{id}/visits:check-in` | CheckInVisit | capturedAt、location、deviceId、deviceCommandId | 201 |
| `POST /visits/{id}:check-out` | CheckOutVisit | capturedAt、resultCode、operationRefs | 200 |
| `POST /visits/{id}:interrupt` | InterruptVisit | exceptionCode、note、evidenceRefs | 200 |
| `POST /visits/{id}/field-operations` | StartFieldOperation | operationType、taskId | 201 |
| `POST /field-operations/{id}/submissions` | SubmitFieldOperation | formSubmissionRef、evidenceSetRef、resultCode | 201 |
| `GET /work-orders/{id}/visits` | 上门历史 | — | 200 |
| `GET /visits/{id}` | 上门 Visit 详情（M159；OpenAPI 权威） | — | 200 |
| `GET /field-operations/{id}` | 作业与提交版本 | — | 200 |

### 2.1 Check-in 位置

```json
{
  "capturedAt": "2026-07-15T09:12:00+08:00",
  "deviceCommandId": "DEV-CMD-001",
  "deviceId": "DEVICE-01",
  "location": {
    "latitude": 36.067,
    "longitude": 120.382,
    "accuracyMeters": 18.5
  },
  "offline": false
}
```

服务器返回权威 `receivedAt`、geofenceResult、Visit ID 和版本。

## 3. 表单

| 方法与路径 | 命令/用途 | 必需载荷 | 成功 |
|---|---|---|---|
| `GET /tasks/{taskId}/forms` | 当前任务表单及版本 | — | 200 |
| `GET /form-versions/{id}` | 已发布 data/UI schema 和规则包 | — | 200 |
| `PUT /tasks/{taskId}/form-drafts/{formCode}` | SaveFormDraft | formVersionId、values、baseRevision | 200 |
| `GET /tasks/{taskId}/form-drafts/{formCode}` | 获取自己的草稿 | — | 200 |
| `POST /tasks/{taskId}/form-submissions` | SubmitForm | formVersionId、values、prefillVersion | 201 |
| `POST /form-submissions/{id}:supersede` | CorrectFormSubmission | values、reason、sourceReviewRef | 201 |
| `GET /form-submissions/{id}` | 不可变提交及验证 | — | 200 |

提交成功返回 submission ID/version、validationStatus、字段错误/警告和 contentDigest。`INVALID` 提交不能用于完成任务。

表单值中的资料字段使用 EvidenceItem ID/EvidenceRevision ID，不接受永久对象存储 URL。

## 4. 资料槽位与上传

| 方法与路径 | 命令/用途 | 必需载荷 | 成功 |
|---|---|---|---|
| `GET /tasks/{taskId}/evidence-slots` | 条件解析后的资料要求 | — | 200 |
| `POST /evidence-slots/{slotId}/upload-sessions` | CreateUploadSession | mimeType、size、checksum、captureMetadata | 201 |
| `POST /upload-sessions/{id}:complete-part` | CompleteUploadPart | partNo、etag | 200 |
| `POST /upload-sessions/{id}:finalize` | FinalizeUpload | checksum、parts、deviceCommandId? | 201/202 |
| `GET /evidence-items/{id}` | 逻辑资料及全部版本 | — | 200 |
| `GET /evidence-revisions/{id}` | 文件元数据、校验和审核投影 | — | 200 |
| `POST /evidence-revisions/{id}:invalidate` | InvalidateEvidence | reason、approvalRef? | 200 |
| `POST /evidence-revisions/{id}/download-authorizations` | 申请短时下载 | purpose | 201 |
| `POST /tasks/{taskId}/evidence-set-snapshots` | CreateEvidenceSetSnapshot | purpose、memberRevisionIds | 201 |
| `GET /evidence-set-snapshots/{id}` | 查询冻结成员、版本和摘要 | — | 200 |

UploadSession 返回受限上传地址、允许分片、过期时间和最大字节数。Finalize 前服务器校验对象路径、长度和 checksum。

创建 EvidenceSetSnapshot 时，服务端必须校验：所有 revision 均属于当前 Task 可用槽位；成员未失效、未隔离且无重复；成员的校验/审核资格满足 `purpose` 要求；必需槽位和数量约束满足；当前主体有权读取每个成员。集合摘要和成员资格结果一并冻结，客户端不能跨工单拼接资料。

### 4.1 CaptureMetadata

```json
{
  "source": "CAMERA",
  "capturedAt": "2026-07-15T10:20:00+08:00",
  "location": {
    "latitude": 36.067,
    "longitude": 120.382,
    "accuracyMeters": 15
  },
  "deviceId": "DEVICE-01",
  "appVersion": "1.3.0",
  "offline": true,
  "watermarkPolicyVersionId": "WM-3-V2"
}
```

实际上传者从认证上下文获取。代上传需要单独 `onBehalfOf` 业务字段、能力和原因，不能通过伪造 userId 实现。

## 5. 审核与整改

| 方法与路径 | 命令/用途 | 必需载荷 | 成功 |
|---|---|---|---|
| `POST /review-cases` | CreateReviewCase（内部编排） | type、scopeRef、policyVersion | 201 |
| `GET /review-cases/{id}` | 审核范围、当前决定和历史 | — | 200 |
| `POST /review-cases/{id}:decide` | DecideReview | targetDecisions、note? | 200 |
| `POST /review-cases/{id}:force-approve` | ForceApprove | reason、approvalRef | 200 |
| `POST /review-cases/{id}:reopen` | ReopenReview | reason、triggerRef、approvalRef? | 201 |
| `POST /internal/client-review-cases` | 登记已回传车企并创建 CLIENT ReviewCase（仅适配层服务主体） | sourceReviewCaseId、externalSubmissionRef、callbackBatchRef、mappingVersionId、policyVersion | 201 |
| `GET /correction-cases/{id}` | 整改项与轮次 | — | 200 |
| `POST /correction-cases/{id}:resubmit` | ResubmitCorrection | correctedTargetRefs | 200 |
| `POST /internal/external-review-receipts` | RecordExternalReviewReceipt（仅适配层服务主体） | inboundEnvelopeId、canonicalMessageId、reviewCaseRef、externalKey、callbackBatchRef、mappingVersionId、result、affectedTargets、payloadRef | 200 |

整改分配通过关联整改 Task 的责任人策略、领取或任务分配命令完成；CorrectionCase 不维护自己的 assignee 或 SLA。

### 5.1 单项决定

```json
{
  "targetDecisions": [
    {
      "targetType": "EvidenceRevision",
      "targetId": "EVD-REV-101",
      "targetVersion": 1,
      "decision": "APPROVED"
    },
    {
      "targetType": "EvidenceRevision",
      "targetId": "EVD-REV-102",
      "targetVersion": 1,
      "decision": "REJECTED",
      "reasonCodes": ["IMAGE.BLUR"],
      "note": "请重新现场拍摄铭牌"
    }
  ]
}
```

审核 API 不接受 `overallDecision`，也不允许直接修改资料或表单值。服务端依据 policyVersion 和单项决定计算整组结果，并在响应中返回 `derivedOverallDecision` 与命中解释。

### 5.2 外部审核回执

```json
{
  "reviewCaseRef": {"id": "REVIEW-CLIENT-01", "version": 2},
  "externalKey": "CLIENT-REVIEW-8899",
  "callbackBatchRef": "CALLBACK-BATCH-19",
  "mappingVersionId": "MAP-17-V6",
  "result": "REJECTED",
  "reasonCodes": ["CLIENT.IMAGE.BLUR"],
  "affectedTargets": [
    {"type": "EvidenceRevision", "id": "EVD-REV-102", "version": 1},
    {"type": "EvidenceRevision", "id": "REPORT-EVD-REV-55", "version": 3, "role": "GENERATED_REPORT"}
  ],
  "payloadRef": "INBOUND-PAYLOAD-991"
}
```

该端点不对车企或普通用户开放。车企请求必须先经过 Connector 的验签、原文留存、入站幂等和 CanonicalMessage 映射；适配层使用服务主体调用，并强制关联 inboundEnvelopeId/canonicalMessageId。M55 起，回执只接受 CLIENT ReviewCase，且 callbackBatchRef、mappingVersionId 必须精确匹配 Case 冻结值；M54 继续校验受影响对象属于冻结 Snapshot。随后追加 ReviewDecision 并触发客服协调，不直接修改师傅 Task。

## 6. 移动工作包与离线同步

| 方法与路径 | 用途 |
|---|---|
| `POST /tasks/{taskId}/mobile-work-packages` | 为当前设备签发离线工作包 |
| `GET /mobile-work-packages/{id}/manifest` | 配置、表单、资料和预填摘要 |
| `POST /mobile-sync/batches` | 批量提交离线命令清单 |
| `GET /mobile-sync/batches/{id}` | 每条命令的权威处理结果 |
| `GET /sync-conflicts/{id}` | 冲突详情和允许解决动作 |

离线批次不保证整体事务；每条命令独立幂等、独立返回 `ACCEPTED/REJECTED/CONFLICT/RETRYABLE`。存在依赖的命令携带 `dependsOnDeviceCommandIds`，服务器按拓扑顺序处理。

## 7. 事件目录

| 事件 | 关键载荷 |
|---|---|
| `AppointmentConfirmed` | appointment、revision、window、participants |
| `ContactAttemptRecorded` | contact attempt、task、channel、result、business timestamps |
| `AppointmentCancelled` | appointment、terminal revision、reason |
| `AppointmentNoShowMarked` | appointment、party ref、reason、evidence refs |
| `VisitCheckedIn` | visit、appointment、captured/received time、geofence result |
| `FormSubmitted` | task、form version、submission version、validation status |
| `EvidenceStored` | slot、item、revision、checksum、capture summary |
| `EvidenceValidationCompleted` | revision、policy/model version、result summary |
| `ReviewDecided` | review case、scope version、decision IDs |
| `CorrectionRequested` | correction case、rejected target refs、dueAt |
| `CorrectionResubmitted` | correction case、new target refs、round |
| `ExternalReviewRejected` | review case、external receipt、reason mapping、affected refs |

事件不包含原图 URL、手机号、完整地址或完整表单值。

## 8. 错误码补充

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `APPOINTMENT_VERSION_CONFLICT` | 409 | 预约已被他人改约 |
| `APPOINTMENT_WINDOW_NOT_ENDED` | 409 | 预约窗口尚未结束，不能标记爽约 |
| `TECHNICIAN_ASSIGNMENT_CHANGED` | 409 | 离线期间任务已改派 |
| `FORM_VERSION_NOT_ALLOWED` | 409/422 | 表单版本不再允许当前提交 |
| `FORM_VALIDATION_FAILED` | 422 | 服务端字段校验失败 |
| `EVIDENCE_SLOT_NOT_REQUIRED` | 409 | 资料槽位已失效 |
| `UPLOAD_CHECKSUM_MISMATCH` | 422 | 上传对象摘要不一致 |
| `EVIDENCE_NOT_READY_FOR_REVIEW` | 409 | 文件尚未完成校验 |
| `REVIEW_TARGET_VERSION_CONFLICT` | 409 | 审核对象已有新版本 |
| `OFFLINE_COMMAND_CONFLICT` | 409 | 离线基线与服务器冲突 |
