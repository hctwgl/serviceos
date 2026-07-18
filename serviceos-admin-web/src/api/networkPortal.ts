/** Network Portal API：networkId 只经 X-Network-Context，禁止 query-param。 */
import { apiGet, apiPost, type HttpStatusError } from './client'

export type NetworkPortalTechnicianItem = {
  membershipId: string
  technicianProfileId: string
  principalId: string
  displayName: string
  profileStatus: string
  membershipStatus: string
  validFrom: string
  validTo: string | null
  /** M206：ACTIVE 关系乐观版本（附加字段，terminate 亦可从 memberships 列表取） */
  membershipVersion?: number
}

/** M227/M231：预约摘要（对齐 Admin WorkOrderWorkspaceAppointmentSummary）。 */
export type NetworkPortalWorkspaceAppointmentSummary = {
  appointmentId: string
  taskId: string
  type: string
  status: string
  assignedNetworkId: string | null
  technicianId: string | null
  currentRevisionNo: number
  windowStart: string | null
  windowEnd: string | null
  timezone: string | null
  estimatedDurationMinutes: number | null
  aggregateVersion: number
  createdAt: string
}

/** M227/M232：联系尝试摘要（对齐 Admin；无 party/note/recording/actor）。 */
export type NetworkPortalWorkspaceContactAttemptSummary = {
  contactAttemptId: string
  taskId: string
  projectId: string
  workOrderId: string
  channel: string
  startedAt: string
  endedAt: string
  resultCode: string
  nextContactAt: string | null
  createdAt: string
}

/** M234：目录页薄 SLA 风险摘要（计数语义同工作台/工作区）。 */
export type NetworkPortalDirectorySlaRiskSummary = {
  workOrderId: string
  taskId: string | null
  openCount: number
  breachedCount: number
}

export type NetworkPortalPage<T> = {
  networkId: string
  items: T[]
  asOf: string
  /** Soft-gated；缺 NETWORK `technician.readOwnNetwork` 时省略（工单/任务目录页）。 */
  technicians?: NetworkPortalTechnicianItem[]
  /** Soft-gated；缺 NETWORK `networkPortal.manageAppointment` 时省略（工单/任务目录页）。 */
  appointments?: NetworkPortalWorkspaceAppointmentSummary[]
  /** Soft-gated；缺 NETWORK `networkPortal.manageAppointment` 时省略（工单/任务目录页）。 */
  contactAttempts?: NetworkPortalWorkspaceContactAttemptSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时省略（工单/任务目录页）。 */
  corrections?: NetworkPortalWorkspaceCorrectionCaseSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时与 evidenceItems 同时省略（工单/任务目录页）。 */
  evidenceSlots?: NetworkPortalWorkspaceEvidenceSlotSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时与 evidenceSlots 同时省略（工单/任务目录页）。 */
  evidenceItems?: NetworkPortalWorkspaceEvidenceItemSummary[]
  /** Soft-gated；缺 NETWORK `sla.read` 时省略（工单/任务目录页）。 */
  slaRiskSummaries?: NetworkPortalDirectorySlaRiskSummary[]
}

export type NetworkPortalWorkOrderItem = {
  workOrderId: string
  projectId: string | null
  taskIds: string[]
  businessType: string | null
  technicianId: string | null
  effectiveFrom: string | null
  /** M236：非 PII 工单头；缺失时为 null。 */
  brandCode?: string | null
  serviceProductCode?: string | null
  provinceCode?: string | null
  cityCode?: string | null
  districtCode?: string | null
  /** M236：工单接收时间（产品「更新时间」MVP 映射）。 */
  receivedAt?: string | null
}

export type NetworkPortalTaskItem = {
  taskId: string
  workOrderId: string
  projectId: string | null
  taskType: string | null
  taskKind: string | null
  stageCode: string | null
  status: string | null
  businessType: string | null
  technicianId: string | null
  effectiveFrom: string | null
  /** M236：所属工单非 PII 头。 */
  brandCode?: string | null
  serviceProductCode?: string | null
  provinceCode?: string | null
  cityCode?: string | null
  districtCode?: string | null
  receivedAt?: string | null
}

export type NetworkPortalMembershipItem = {
  id: string
  serviceNetworkId: string
  technicianProfileId: string
  status: string
  validFrom: string
  validTo: string | null
  version: number
  createdAt: string
  terminatedAt: string | null
  terminateReason: string | null
}

export type NetworkPortalCapacityItem = {
  capacityCounterId: string
  businessType: string
  maxUnits: number
  occupiedUnits: number
  availableUnits: number
  version: number
  updatedAt: string
}

export type NetworkPortalWorkbench = {
  networkId: string
  activeWorkOrderCount: number
  activeTaskCount: number
  activeTechnicianCount: number
  capacity: NetworkPortalCapacityItem[]
  asOf: string
  /** 基座成功时始终返回；无 TECHNICIAN assignee 的 ACTIVE 任务数 */
  unassignedTechnicianTaskCount?: number
  /** 需 evidence.read；缺能力时省略 */
  openCorrectionCaseCount?: number
  /** 需 operations.exception.read；缺能力时省略 */
  openOperationalExceptionCount?: number
  /** 需 technician.readOwnNetwork；缺能力时省略 */
  pendingQualificationCount?: number
  /** Soft-gated；缺 NETWORK `sla.read` 时省略，不得用 0 伪装无权限。 */
  slaSummary?: NetworkPortalWorkOrderWorkspaceSlaSummary
}

function networkHeaders(networkContextId: string): Record<string, string> {
  return { 'X-Network-Context': networkContextId }
}

export function listNetworkPortalWorkOrders(networkContextId: string) {
  return apiGet<NetworkPortalPage<NetworkPortalWorkOrderItem>>(
    '/network-portal/work-orders',
    {},
    networkHeaders(networkContextId),
  )
}

/** M221：工作区薄 SLA 摘要；缺 NETWORK sla.read 时省略。 */
export type NetworkPortalWorkOrderWorkspaceSlaSummary = {
  openCount: number
  breachedCount: number
}

/** M222：字段对齐 Admin WorkOrderWorkspaceVisitSummary（无 GPS/note/device）。 */
export type NetworkPortalWorkspaceVisitSummary = {
  visitId: string
  taskId: string
  appointmentId: string
  visitSequence: number
  technicianId: string
  networkId: string | null
  status: string
  checkInCapturedAt: string
  checkInReceivedAt: string
  geofenceResult: string
  policyDecision: string
  checkOutCapturedAt: string | null
  checkOutReceivedAt: string | null
  resultCode: string | null
  exceptionCode: string | null
  aggregateVersion: number
}

/** M222：字段对齐 Admin WorkOrderWorkspaceFormSubmissionSummary（无 values）。 */
export type NetworkPortalWorkspaceFormSubmissionSummary = {
  submissionId: string
  taskId: string
  projectId: string
  formVersionId: string
  formKey: string
  submissionVersion: number
  contentDigest: string
  validationStatus: string
  errorCount: number
  warningCount: number
  submittedAt: string
}

/** M223：字段对齐 Admin WorkOrderWorkspaceEvidenceSlotSummary（无 definition JSON）。 */
export type NetworkPortalWorkspaceEvidenceSlotSummary = {
  slotId: string
  taskId: string
  projectId: string
  templateKey: string
  templateVersion: string
  requirementCode: string
  occurrenceKey: string
  requirementName: string
  mediaType: string
  required: boolean
  minCount: number
  maxCount: number | null
  status: string
  resolvedAt: string
  slotGeneration: number
  active: boolean
  transition: string
  requiredDisposition: string
}

/** M223：字段对齐 Admin WorkOrderWorkspaceEvidenceItemSummary（无 file/metadata）。 */
export type NetworkPortalWorkspaceEvidenceItemSummary = {
  evidenceItemId: string
  taskId: string
  projectId: string
  evidenceSlotId: string
  itemOrdinal: number
  status: string
  revisionCount: number
  latestRevisionNumber: number | null
  latestRevisionStatus: string | null
}

/** M225：字段对齐 Admin WorkOrderWorkspaceCorrectionResubmissionSummary。 */
export type NetworkPortalWorkspaceCorrectionResubmissionSummary = {
  correctionResubmissionId: string
  resubmissionOrdinal: number
  evidenceSetSnapshotId: string
  submittedAt: string
}

/** M225：字段对齐 Admin WorkOrderWorkspaceCorrectionCaseSummary（无 createdBy/waiveNote）。 */
export type NetworkPortalWorkspaceCorrectionCaseSummary = {
  correctionCaseId: string
  taskId: string
  projectId: string
  sourceReviewCaseId: string
  sourceReviewDecisionId: string
  reasonCodes: string[]
  correctionTaskId: string | null
  status: string
  createdAt: string
  latestResubmissionSnapshotId: string | null
  closedAt: string | null
  waivedAt: string | null
  resubmissions: NetworkPortalWorkspaceCorrectionResubmissionSummary[]
}

/** M229：字段对齐 Admin WorkOrderWorkspaceReviewDecisionSummary（无 note/approvalRef/decidedBy）。 */
export type NetworkPortalWorkspaceReviewDecisionSummary = {
  reviewDecisionId: string
  decisionOrdinal: number
  decision: string
  decisionSource: string
  reasonCodes: string[]
  decidedAt: string
}

/** M229：字段对齐 Admin WorkOrderWorkspaceReviewCaseSummary（无 createdBy/digest）。 */
export type NetworkPortalWorkspaceReviewCaseSummary = {
  reviewCaseId: string
  taskId: string
  projectId: string
  evidenceSetSnapshotId: string
  scopeType: string
  origin: string
  policyVersion: string
  status: string
  createdAt: string
  decidedAt: string | null
  sourceReviewCaseId: string | null
  externalSubmissionRef: string | null
  callbackBatchRef: string | null
  mappingVersionId: string | null
  reopenedFromReviewCaseId: string | null
  reopenTriggerRef: string | null
  decisions: NetworkPortalWorkspaceReviewDecisionSummary[]
}

/** M213：限定工单工作区薄快照（ACTIVE NETWORK 责任门禁）。 */
export type NetworkPortalWorkOrderWorkspace = {
  networkId: string
  workOrderId: string
  projectId: string | null
  taskIds: string[]
  businessType: string | null
  technicianId: string | null
  effectiveFrom: string | null
  tasks: NetworkPortalTaskItem[]
  /** Soft-gated；缺 NETWORK `sla.read` 时省略，不得用 0 伪装无权限。 */
  slaSummary?: NetworkPortalWorkOrderWorkspaceSlaSummary
  /** Soft-gated；缺 NETWORK `visit.read` 时省略，不得用空数组伪装无权限。 */
  visits?: NetworkPortalWorkspaceVisitSummary[]
  /** Soft-gated；缺 NETWORK `form.read` 时省略，不得用空数组伪装无权限。 */
  formSubmissions?: NetworkPortalWorkspaceFormSubmissionSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时与 evidenceItems 同时省略。 */
  evidenceSlots?: NetworkPortalWorkspaceEvidenceSlotSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时与 evidenceSlots 同时省略。 */
  evidenceItems?: NetworkPortalWorkspaceEvidenceItemSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时省略，不得用空数组伪装无权限。 */
  corrections?: NetworkPortalWorkspaceCorrectionCaseSummary[]
  /** Soft-gated；缺 NETWORK `evidence.read` 时省略，不得用空数组伪装无权限。 */
  reviews?: NetworkPortalWorkspaceReviewCaseSummary[]
  /** Soft-gated；缺 NETWORK `operations.exception.read` 时省略，不得用空数组伪装无权限。 */
  exceptions?: NetworkPortalExceptionItem[]
  /** Soft-gated；缺 NETWORK `networkPortal.manageAppointment` 时与 contactAttempts 同时省略。 */
  appointments?: NetworkPortalWorkspaceAppointmentSummary[]
  /** Soft-gated；缺 NETWORK `networkPortal.manageAppointment` 时与 appointments 同时省略。 */
  contactAttempts?: NetworkPortalWorkspaceContactAttemptSummary[]
  /** Soft-gated；缺 NETWORK `technician.readOwnNetwork` 时省略，不得用空数组伪装无权限。 */
  technicians?: NetworkPortalTechnicianItem[]
  asOf: string
}

export function getNetworkPortalWorkOrderWorkspace(
  networkContextId: string,
  workOrderId: string,
) {
  return apiGet<NetworkPortalWorkOrderWorkspace>(
    `/network-portal/work-orders/${workOrderId}/workspace`,
    {},
    networkHeaders(networkContextId),
  )
}

export function listNetworkPortalTasks(networkContextId: string) {
  return apiGet<NetworkPortalPage<NetworkPortalTaskItem>>(
    '/network-portal/tasks',
    {},
    networkHeaders(networkContextId),
  )
}

export function listNetworkPortalTechnicians(networkContextId: string) {
  return apiGet<NetworkPortalPage<NetworkPortalTechnicianItem>>(
    '/network-portal/technicians',
    {},
    networkHeaders(networkContextId),
  )
}

export type NetworkTechnicianMembership = {
  id: string
  serviceNetworkId: string
  technicianProfileId: string
  status: string
  validFrom: string
  validTo: string | null
  version: number
  createdAt: string
}

export type TechnicianQualification = {
  id: string
  technicianProfileId: string
  qualificationCode: string
  status: string
  validFrom: string
  validTo: string | null
  version: number
  submittedAt: string
}

/** M204：绑定本网点师傅服务关系；禁止提交 networkId。 */
export function createNetworkPortalTechnicianMembership(
  networkContextId: string,
  body: { technicianProfileId: string; validFrom: string },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<NetworkTechnicianMembership>('/network-portal/technician-memberships', {
    body,
    idempotencyKey,
    headers: networkHeaders(networkContextId),
  })
}

/** M204：终止本网点师傅服务关系。 */
export function terminateNetworkPortalTechnicianMembership(
  networkContextId: string,
  membershipId: string,
  body: { reason: string },
  aggregateVersion: number,
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<NetworkTechnicianMembership>(
    `/network-portal/technician-memberships/${membershipId}:terminate`,
    {
      body,
      idempotencyKey,
      ifMatch: `"${aggregateVersion}"`,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M204：提交本网点师傅资质（PENDING）。 */
export function submitNetworkPortalTechnicianQualification(
  networkContextId: string,
  body: {
    technicianProfileId: string
    qualificationCode: string
    validFrom: string
    validTo?: string | null
  },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<TechnicianQualification>('/network-portal/technician-qualifications', {
    body,
    idempotencyKey,
    headers: networkHeaders(networkContextId),
  })
}

export function getNetworkPortalWorkbench(networkContextId: string) {
  return apiGet<NetworkPortalWorkbench>(
    '/network-portal/workbench',
    {},
    networkHeaders(networkContextId),
  )
}

/** M208：独立产能页；复用 M194 GET /network-portal/capacity。 */
export function listNetworkPortalCapacity(networkContextId: string) {
  return apiGet<NetworkPortalPage<NetworkPortalCapacityItem>>(
    '/network-portal/capacity',
    {},
    networkHeaders(networkContextId),
  )
}

export type ManualServiceAssignmentReceipt = {
  taskId: string
  workOrderId: string
  networkServiceAssignmentId: string
  technicianServiceAssignmentId: string
  networkAssigneeId: string
  technicianAssigneeId: string
  occurredAt: string
}

/** M200：同网点改派师傅；需已有不同 ACTIVE TECHNICIAN。 */
export function reassignNetworkPortalTechnician(
  networkContextId: string,
  taskId: string,
  body: { technicianAssigneeId: string; businessType: string; reasonCode: string },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<ManualServiceAssignmentReceipt>(
    `/network-portal/tasks/${taskId}:reassign-technician`,
    {
      body,
      idempotencyKey,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M196：指派师傅；不提交 networkAssigneeId，服务端强制等于可信上下文网点。 */
export function assignNetworkPortalTechnician(
  networkContextId: string,
  taskId: string,
  body: { technicianAssigneeId: string; businessType: string },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<ManualServiceAssignmentReceipt>(
    `/network-portal/tasks/${taskId}:assign-technician`,
    {
      body,
      idempotencyKey,
      headers: networkHeaders(networkContextId),
    },
  )
}

export type AppointmentWindow = {
  start: string
  end: string
  timezone: string
  estimatedDurationMinutes: number
}

export type AppointmentCommandReceipt = {
  appointmentId: string
  revisionId: string
  status: string
  revisionNo: number
  aggregateVersion: number
  occurredAt: string
}

export type NetworkPortalAppointmentRevision = {
  revisionId?: string
  revisionNo: number
  window: AppointmentWindow
  /** 契约含 addressRef；UI 禁止渲染（ADR-054 / ADR-076）。 */
  addressRef?: string
  addressVersion?: string
  note?: string | null
  /** M238：确认渠道 / 确认方类型（非 PII）；缺省表示尚未确认。 */
  confirmationChannel?: string | null
  confirmedPartyType?: string | null
  /** 修订操作者；与 Appointment.createdBy 可不同。 */
  createdBy?: string
}

export type NetworkPortalAppointment = {
  appointmentId: string
  taskId: string
  type: string
  status: string
  assignedNetworkId: string | null
  aggregateVersion: number
  currentRevisionNo: number
  /** OpenAPI Appointment.createdBy；product/03 §8「操作者」。 */
  createdBy?: string
  /** M241：OpenAPI Appointment 既有非 PII 范围/时间/动作字段。 */
  projectId?: string
  workOrderId?: string
  technicianId?: string | null
  createdAt?: string
  allowedActions?: string[]
  /** OpenAPI Appointment.revisions；M216/M238/M241 消费 current window/渠道。 */
  revisions?: NetworkPortalAppointmentRevision[]
}

/** M197：列出本网点任务预约。 */
export function listNetworkPortalTaskAppointments(networkContextId: string, taskId: string) {
  return apiGet<NetworkPortalAppointment[]>(
    `/network-portal/tasks/${taskId}/appointments`,
    {},
    networkHeaders(networkContextId),
  )
}

/** M197：提议预约；委托 Admin propose 同形 body。 */
export function proposeNetworkPortalAppointment(
  networkContextId: string,
  taskId: string,
  body: {
    type: string
    window: AppointmentWindow
    addressRef: string
    addressVersion: string
  },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<AppointmentCommandReceipt>(`/network-portal/tasks/${taskId}/appointments`, {
    body,
    idempotencyKey,
    headers: networkHeaders(networkContextId),
  })
}

/** M197：确认预约；confirmedPartyType 仅 NETWORK_MEMBER/NETWORK。 */
export function confirmNetworkPortalAppointment(
  networkContextId: string,
  appointmentId: string,
  body: {
    confirmedPartyType: string
    confirmedPartyRef: string
    confirmationChannel: string
  },
  aggregateVersion: number,
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<AppointmentCommandReceipt>(
    `/network-portal/appointments/${appointmentId}:confirm`,
    {
      body,
      idempotencyKey,
      ifMatch: `"${aggregateVersion}"`,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M198：改约已确认预约；If-Match 来自列表/回执 aggregateVersion。 */
export function rescheduleNetworkPortalAppointment(
  networkContextId: string,
  appointmentId: string,
  body: {
    newWindow: AppointmentWindow
    reasonCode: string
    note?: string | null
  },
  aggregateVersion: number,
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<AppointmentCommandReceipt>(
    `/network-portal/appointments/${appointmentId}:reschedule`,
    {
      body,
      idempotencyKey,
      ifMatch: `"${aggregateVersion}"`,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M198：取消提议中或已确认预约。 */
export function cancelNetworkPortalAppointment(
  networkContextId: string,
  appointmentId: string,
  body: { reasonCode: string; note?: string | null },
  aggregateVersion: number,
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<AppointmentCommandReceipt>(
    `/network-portal/appointments/${appointmentId}:cancel`,
    {
      body,
      idempotencyKey,
      ifMatch: `"${aggregateVersion}"`,
      headers: networkHeaders(networkContextId),
    },
  )
}

export type NetworkPortalContactAttempt = {
  contactAttemptId: string
  taskId: string
  channel: string
  contactedPartyRef: string
  resultCode: string
  actorId: string
  createdAt: string
  /** M240/M241：OpenAPI ContactAttempt 既有非 PII 字段；不渲染 party/note/recording。 */
  projectId?: string
  workOrderId?: string
  startedAt?: string
  endedAt?: string
  nextContactAt?: string | null
}

/** M199：标记本网点预约爽约（CONFIRMED 且窗口已结束）。 */
export function markNetworkPortalAppointmentNoShow(
  networkContextId: string,
  appointmentId: string,
  body: {
    noShowPartyType: string
    noShowPartyRef: string
    reasonCode: string
    evidenceRefs: string[]
  },
  aggregateVersion: number,
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<AppointmentCommandReceipt>(
    `/network-portal/appointments/${appointmentId}:mark-no-show`,
    {
      body,
      idempotencyKey,
      ifMatch: `"${aggregateVersion}"`,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M199：列出本网点任务联系尝试。 */
export function listNetworkPortalTaskContactAttempts(networkContextId: string, taskId: string) {
  return apiGet<NetworkPortalContactAttempt[]>(
    `/network-portal/tasks/${taskId}/contact-attempts`,
    {},
    networkHeaders(networkContextId),
  )
}

/** M199：记录本网点任务联系尝试。 */
export function recordNetworkPortalTaskContactAttempt(
  networkContextId: string,
  taskId: string,
  body: {
    channel: string
    contactedPartyRef: string
    startedAt: string
    endedAt: string
    resultCode: string
    note?: string | null
    nextContactAt?: string | null
    recordingRef?: string | null
  },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<NetworkPortalContactAttempt>(
    `/network-portal/tasks/${taskId}/contact-attempts`,
    {
      body,
      idempotencyKey,
      headers: networkHeaders(networkContextId),
    },
  )
}

export type NetworkPortalEvidenceUploadSession = {
  uploadSessionId: string
  fileId: string
  taskId: string
  evidenceSlotId: string
  evidenceItemId: string | null
  status: string
}

export type NetworkPortalEvidenceItem = {
  evidenceItemId: string
  taskId: string
  evidenceSlotId: string
  status: string
}

export type NetworkPortalCorrectionCase = {
  correctionCaseId: string
  taskId: string
  status: string
  latestResubmissionSnapshotId: string | null
}

export type NetworkPortalCorrectionItem = {
  correctionCaseId: string
  projectId: string
  taskId: string
  sourceReviewCaseId: string
  sourceReviewDecisionId: string
  reasonCodes: string[]
  correctionTaskId: string | null
  status: string
  createdAt: string
  latestResubmissionSnapshotId: string | null
  closedAt: string | null
  waivedAt: string | null
  resubmissionCount: number
}

/** M209：对齐 OpenAPI CorrectionCase（getNetworkPortalCorrectionCase）。 */
export type NetworkPortalCorrectionResubmission = {
  correctionResubmissionId: string
  correctionCaseId: string
  resubmissionOrdinal: number
  evidenceSetSnapshotId: string
  snapshotContentDigest: string
  submittedBy?: string
  submittedAt?: string | null
}

export type NetworkPortalCorrectionDetail = {
  correctionCaseId: string
  projectId: string
  taskId: string
  sourceReviewCaseId: string
  sourceReviewDecisionId: string
  sourceEvidenceSetSnapshotId: string
  sourceSnapshotContentDigest: string
  reasonCodes: string[]
  correctionTaskId?: string | null
  status: string
  createdBy: string
  createdAt: string
  latestResubmissionSnapshotId?: string | null
  closedBy?: string | null
  closedAt?: string | null
  waivedBy?: string | null
  waivedAt?: string | null
  waiveApprovalRef?: string | null
  waiveNote?: string | null
  resubmissions: NetworkPortalCorrectionResubmission[]
}

/** M201：代师傅 Begin 资料上传。 */
export function beginNetworkPortalEvidenceUploadOnBehalf(
  networkContextId: string,
  taskId: string,
  slotId: string,
  body: {
    originalFileName: string
    declaredMimeType: string
    expectedSize: number
    expectedSha256: string
    captureMetadata: Record<string, unknown>
    onBehalfOf: string
    onBehalfReason: string
    evidenceItemId?: string | null
  },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<NetworkPortalEvidenceUploadSession>(
    `/network-portal/tasks/${taskId}/evidence-slots/${slotId}/upload-sessions`,
    {
      body,
      idempotencyKey,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M201：代师傅 Finalize 资料上传。 */
export function finalizeNetworkPortalEvidenceUploadOnBehalf(
  networkContextId: string,
  taskId: string,
  slotId: string,
  uploadSessionId: string,
  body: { actualSha256: string; finalizeCommandId: string },
) {
  return apiPost<NetworkPortalEvidenceItem>(
    `/network-portal/tasks/${taskId}/evidence-slots/${slotId}/upload-sessions/${uploadSessionId}:finalize`,
    {
      body,
      headers: networkHeaders(networkContextId),
    },
  )
}

/** M202：本网点整改队列。 */
export function listNetworkPortalCorrections(
  networkContextId: string,
  params?: { status?: string; taskId?: string; limit?: number },
) {
  return apiGet<NetworkPortalPage<NetworkPortalCorrectionItem>>(
    '/network-portal/correction-cases',
    {
      status: params?.status,
      taskId: params?.taskId,
      limit: params?.limit == null ? undefined : String(params.limit),
    },
    networkHeaders(networkContextId),
  )
}

/** M202/M209：本网点整改详情（CorrectionCase 完整形）。 */
export function getNetworkPortalCorrection(networkContextId: string, correctionCaseId: string) {
  return apiGet<NetworkPortalCorrectionDetail>(
    `/network-portal/correction-cases/${correctionCaseId}`,
    {},
    networkHeaders(networkContextId),
  )
}

export type NetworkPortalExceptionItem = {
  exceptionId: string
  projectId: string | null
  sourceType: string
  category: string
  severity: string
  errorCode: string
  status: string
  workOrderId: string | null
  taskId: string | null
  handlingTaskId: string | null
  occurrenceCount: number
  openedAt: string
  lastDetectedAt: string
  resolvedAt: string | null
  resolutionCode: string | null
  allowedActions: string[]
}

/** M203：本网点运营异常队列。 */
export function listNetworkPortalExceptions(
  networkContextId: string,
  params?: { status?: string; taskId?: string; severity?: string; limit?: number },
) {
  return apiGet<NetworkPortalPage<NetworkPortalExceptionItem>>(
    '/network-portal/operational-exceptions',
    {
      status: params?.status,
      taskId: params?.taskId,
      severity: params?.severity,
      limit: params?.limit == null ? undefined : String(params.limit),
    },
    networkHeaders(networkContextId),
  )
}

/** M203：本网点运营异常详情。 */
export function getNetworkPortalException(networkContextId: string, exceptionId: string) {
  return apiGet<NetworkPortalExceptionItem>(
    `/network-portal/operational-exceptions/${exceptionId}`,
    {},
    networkHeaders(networkContextId),
  )
}

export type NetworkPortalQualificationItem = {
  id: string
  technicianProfileId: string
  qualificationCode: string
  status: string
  validFrom: string
  validTo: string | null
  submittedBy: string
  submittedAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
  version: number
}

/** M205：本网点师傅资质列表。 */
export function listNetworkPortalQualifications(
  networkContextId: string,
  params?: { status?: string; technicianProfileId?: string; limit?: number },
) {
  return apiGet<NetworkPortalPage<NetworkPortalQualificationItem>>(
    '/network-portal/technician-qualifications',
    {
      status: params?.status,
      technicianProfileId: params?.technicianProfileId,
      limit: params?.limit == null ? undefined : String(params.limit),
    },
    networkHeaders(networkContextId),
  )
}

/** M205：本网点师傅资质详情。 */
export function getNetworkPortalQualification(networkContextId: string, qualificationId: string) {
  return apiGet<NetworkPortalQualificationItem>(
    `/network-portal/technician-qualifications/${qualificationId}`,
    {},
    networkHeaders(networkContextId),
  )
}

/** M206：本网点师傅关系列表（含真实 version，供 terminate If-Match）。 */
export function listNetworkPortalTechnicianMemberships(
  networkContextId: string,
  params?: { status?: string; technicianProfileId?: string; limit?: number },
) {
  return apiGet<NetworkPortalPage<NetworkPortalMembershipItem>>(
    '/network-portal/technician-memberships',
    {
      status: params?.status,
      technicianProfileId: params?.technicianProfileId,
      limit: params?.limit == null ? undefined : String(params.limit),
    },
    networkHeaders(networkContextId),
  )
}

/** M206：本网点师傅关系详情。 */
export function getNetworkPortalTechnicianMembership(
  networkContextId: string,
  membershipId: string,
) {
  return apiGet<NetworkPortalMembershipItem>(
    `/network-portal/technician-memberships/${membershipId}`,
    {},
    networkHeaders(networkContextId),
  )
}

/** M201：整改补传 resubmit。 */
export function resubmitNetworkPortalCorrectionCase(
  networkContextId: string,
  correctionCaseId: string,
  body: { evidenceSetSnapshotId: string },
  idempotencyKey = crypto.randomUUID(),
) {
  return apiPost<NetworkPortalCorrectionCase>(
    `/network-portal/correction-cases/${correctionCaseId}:resubmit`,
    {
      body,
      idempotencyKey,
      headers: networkHeaders(networkContextId),
    },
  )
}

export function isPortalContextInvalid(err: unknown): boolean {
  const problem = (err as HttpStatusError | undefined)?.problem
  return (
    (err as HttpStatusError | undefined)?.status === 403 &&
    (problem?.errorCode === 'PORTAL_CONTEXT_INVALID' ||
      problem?.code === 'PORTAL_CONTEXT_INVALID' ||
      problem?.title === 'PORTAL_CONTEXT_INVALID')
  )
}
