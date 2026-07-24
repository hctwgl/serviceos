import { get, newIdempotencyKey, post } from './http'

export type WorkOrderListItem = {
  id: string
  externalOrderCode: string
  projectId: string
  clientCode: string
  serviceProductCode: string
  status: string
  maskedCustomerName: string | null
  maskedCustomerPhone: string | null
  maskedServiceAddress: string | null
  currentStageCode: string | null
  currentTaskType: string | null
  currentTaskStatus: string | null
  currentAssigneeDisplayName: string | null
  currentNetworkDisplayName: string | null
  currentTechnicianDisplayName: string | null
  receivedAt: string
  updatedAt: string
}

export type WorkOrderPage = {
  items: WorkOrderListItem[]
  nextCursor: string | null
  totalCount: number
  totalCountTruncated: boolean
  slaRiskSummaries?: Array<{ workOrderId: string; openCount: number; breachedCount: number }>
  exceptionSummaries?: Array<{ workOrderId: string; openCount: number }>
}

export type WorkOrderWorkspaceTask = {
  taskId: string
  taskType: string
  taskKind: 'HUMAN' | 'AUTOMATED'
  status: string
  stageCode: string | null
  claimedBy: string | null
  version: number
}

export type WorkOrderWorkspace = {
  header: {
    id: string
    projectId: string
    externalOrderCode: string
    clientCode: string
    brandCode: string
    serviceProductCode: string
    status: string
    receivedAt: string
    updatedAt: string
    configurationBundleVersion: string
    version: number
    currentStageCode: string | null
    currentAssigneeDisplayName: string | null
    currentNetworkDisplayName: string | null
    currentTechnicianDisplayName: string | null
  }
  currentTaskSummary: WorkOrderWorkspaceTask | null
  workflowStages: Array<{
    stageCode: string
    sequenceNo: number
    status: 'PENDING' | 'ACTIVE' | 'BLOCKED' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED'
    activatedAt: string
    completedAt: string | null
  }>
  allowedActionLink: string | null
  sectionAvailability: Record<string, 'AVAILABLE' | 'EMPTY' | 'UNAVAILABLE'>
  serviceAssignmentSummary: {
    networkId: string | null
    technicianId: string | null
  } | null
  slaSummary: { openCount: number; breachedCount: number } | null
  exceptionSummary: { openCount: number } | null
  projectPersonnel: Array<{
    positionCode: 'CUSTOMER_SERVICE_MANAGER' | 'PROJECT_MANAGER' | 'PROJECT_ASSISTANT'
    positionName: string
    principalId: string | null
    displayName: string | null
    requestedRegionCode: string
    matchedRegionCode: string | null
    matchedRegionName: string | null
    matchStatus: 'ASSIGNED' | 'MISSING' | 'DATA_INCOMPLETE'
    inherited: boolean
    matchedAt: string
    adjustmentReason: string | null
  }>
  maskedCustomerName: string | null
  maskedCustomerPhone: string | null
  maskedServiceAddress: string | null
  timelineFreshnessStatus: string
  sourceVersions: { workOrderVersion: number }
  meta: { asOf: string; freshnessStatus: string }
}

/**
 * 工单工作区时间线条目。eventType/category 为可扩展枚举，后端会持续合并新领域事件，
 * 客户端必须容忍未知值（展示时未知事件原样显示英文码）。
 */
export type WorkOrderTimelineItem = {
  id: string
  category: string
  eventType: string
  occurredAt: string
  actorId: string | null
  resourceType: string
  resourceCode: string | null
  outcomeCode: string | null
}

export type WorkOrderWorkspaceTimelineSectionData = {
  items: WorkOrderTimelineItem[]
  nextCursor: string | null
  lastProjectedAt: string | null
  freshnessStatus: 'FRESH' | 'LAGGING' | 'UNKNOWN' | 'REBUILDING'
}

export type WorkOrderWorkspaceFormSummary = {
  taskId: string
  formVersionId: string
  formKey: string
  semanticVersion: string
  schemaVersion: string
  contentDigest: string
}

/**
 * 表单提交摘要。注意：工作区契约明确不返回 values/submittedBy，
 * 字段值平铺只在后端将来补充 values 时生效。
 */
export type WorkOrderWorkspaceFormSubmissionSummary = {
  submissionId: string
  taskId: string
  projectId: string
  formVersionId: string
  formKey: string
  submissionVersion: number
  contentDigest: string
  validationStatus: 'VALIDATED' | 'INVALID'
  errorCount: number
  warningCount: number
  submittedAt: string
}

export type WorkOrderWorkspaceEvidenceSlotSummary = {
  slotId: string
  taskId: string
  projectId: string
  templateKey: string
  templateVersion: string
  requirementCode: string
  occurrenceKey: string
  requirementName: string
  mediaType: 'PHOTO' | 'VIDEO' | 'DOCUMENT' | 'SIGNATURE' | 'GENERATED_REPORT'
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

/**
 * 资料项安全元数据。latestRevisionId 用于发起受控 download-authorization 预览；
 * 契约不返回创建时间、objectKey 或永久 URL，元数据展示不得虚构这些字段。
 */
export type WorkOrderWorkspaceEvidenceItemSummary = {
  evidenceItemId: string
  taskId: string
  projectId: string
  evidenceSlotId: string
  itemOrdinal: number
  status: 'OPEN' | 'SUBMITTED' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'LOCKED'
  revisionCount: number
  latestRevisionNumber: number | null
  latestRevisionStatus:
    | 'STORED'
    | 'VALIDATING'
    | 'VALIDATED'
    | 'VALIDATION_FAILED'
    | 'QUARANTINED'
    | 'INVALIDATED'
    | null
  latestRevisionId: string | null
  latestMimeType: string | null
}

export type WorkOrderWorkspaceFormsEvidenceSectionData = {
  forms: WorkOrderWorkspaceFormSummary[] | null
  formSubmissions: WorkOrderWorkspaceFormSubmissionSummary[] | null
  evidenceSlots: WorkOrderWorkspaceEvidenceSlotSummary[] | null
  evidenceItems: WorkOrderWorkspaceEvidenceItemSummary[] | null
  nextCursor: string | null
}

export type WorkOrderWorkspaceReviewDecisionSummary = {
  reviewDecisionId: string
  decisionOrdinal: number
  decision: 'APPROVED' | 'REJECTED' | 'FORCE_APPROVED'
  decisionSource: 'INTERNAL' | 'EXTERNAL'
  reasonCodes: string[]
  decidedAt: string
}

export type WorkOrderWorkspaceReviewCaseSummary = {
  reviewCaseId: string
  taskId: string
  projectId: string
  evidenceSetSnapshotId: string
  scopeType: 'EVIDENCE_SET_SNAPSHOT'
  origin: 'INTERNAL' | 'CLIENT'
  policyVersion: string
  status: 'OPEN' | 'APPROVED' | 'REJECTED' | 'FORCE_APPROVED' | 'REOPENED'
  createdAt: string
  decidedAt: string | null
  sourceReviewCaseId: string | null
  externalSubmissionRef: string | null
  callbackBatchRef: string | null
  mappingVersionId: string | null
  reopenedFromReviewCaseId: string | null
  reopenTriggerRef: string | null
  decisions: WorkOrderWorkspaceReviewDecisionSummary[]
}

export type WorkOrderWorkspaceCorrectionResubmissionSummary = {
  correctionResubmissionId: string
  resubmissionOrdinal: number
  evidenceSetSnapshotId: string
  submittedAt: string
}

export type WorkOrderWorkspaceCorrectionCaseSummary = {
  correctionCaseId: string
  taskId: string
  projectId: string
  sourceReviewCaseId: string
  sourceReviewDecisionId: string
  reasonCodes: string[]
  correctionTaskId: string | null
  status: 'OPEN' | 'IN_PROGRESS' | 'RESUBMITTED' | 'CLOSED' | 'WAIVED'
  createdAt: string
  latestResubmissionSnapshotId: string | null
  closedAt: string | null
  waivedAt: string | null
  resubmissions: WorkOrderWorkspaceCorrectionResubmissionSummary[]
}

export type WorkOrderWorkspaceReviewsCorrectionsSectionData = {
  reviews: WorkOrderWorkspaceReviewCaseSummary[] | null
  corrections: WorkOrderWorkspaceCorrectionCaseSummary[] | null
  nextCursor: string | null
}

export type WorkOrderWorkspaceVisitSummary = {
  visitId: string
  taskId: string
  appointmentId: string
  visitSequence: number
  technicianId: string
  networkId: string | null
  status: 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED'
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

export type WorkOrderWorkspaceAppointmentSummary = {
  appointmentId: string
  taskId: string
  type: 'SURVEY' | 'INSTALLATION' | 'REPAIR' | 'CORRECTION' | 'SECOND_VISIT'
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

export type WorkOrderWorkspaceContactAttemptSummary = {
  contactAttemptId: string
  taskId: string
  projectId: string
  workOrderId: string
  channel: string
  startedAt: string
  endedAt: string
  resultCode:
    | 'CONNECTED'
    | 'NO_ANSWER'
    | 'BUSY'
    | 'WRONG_NUMBER'
    | 'USER_REQUESTED_LATER'
    | 'INVALID_CONTACT'
  nextContactAt: string | null
  createdAt: string
}

export type WorkOrderWorkspaceAppointmentsVisitsSectionData = {
  visits: WorkOrderWorkspaceVisitSummary[] | null
  appointments: WorkOrderWorkspaceAppointmentSummary[] | null
  contactAttempts: WorkOrderWorkspaceContactAttemptSummary[] | null
  nextCursor: string | null
}

export type WorkOrderWorkspaceInboundEnvelopeSummary = {
  inboundEnvelopeId: string
  projectId: string
  connectorVersionId: string
  messageType: string
  externalMessageId: string
  signatureStatus: 'VALID'
  processingStatus: 'COMPLETED'
  mappingVersionId: string
  canonicalMessageId: string
  resultCode: string
  resultType: 'WORK_ORDER'
  resultId: string
  receivedAt: string
  completedAt: string
  correlationId: string
}

export type WorkOrderWorkspaceDeliveryAttemptSummary = {
  deliveryAttemptId: string
  attemptNo: number
  taskExecutionAttemptId: string
  requestDate: string
  status: string
  httpStatus: number | null
  resultCode: string | null
  startedAt: string
  finishedAt: string | null
}

export type WorkOrderWorkspaceExternalAcknowledgementSummary = {
  acknowledgementId: string
  acknowledgementType: string
  result: string
  reasonCode: string | null
  mappingVersionId: string
  receivedAt: string
}

export type WorkOrderWorkspaceDeliveryReplaySummary = {
  replayRequestId: string
  executionTaskId: string | null
  status: string
  resultCode: string | null
  requestedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export type WorkOrderWorkspaceOutboundDeliverySummary = {
  deliveryId: string
  projectId: string
  connectorVersionId: string
  mappingVersionId: string
  businessMessageType: string
  businessKey: string
  sourceReviewCaseId: string
  sourceTaskId: string
  sourceWorkOrderId: string
  sourceSnapshotId: string
  externalOrderCode: string
  executionTaskId: string | null
  status: 'PENDING' | 'SENDING' | 'DELIVERED' | 'ACKNOWLEDGED' | 'REJECTED' | 'FAILED_FINAL' | 'UNKNOWN'
  clientReviewCaseId: string | null
  reviewRouteId: string | null
  aggregateVersion: number
  createdAt: string
  deliveredAt: string | null
  acknowledgedAt: string | null
  attempts: WorkOrderWorkspaceDeliveryAttemptSummary[]
  acknowledgements: WorkOrderWorkspaceExternalAcknowledgementSummary[]
  replayRequests: WorkOrderWorkspaceDeliveryReplaySummary[]
}

export type WorkOrderWorkspaceIntegrationSectionData = {
  inboundEnvelopes: WorkOrderWorkspaceInboundEnvelopeSummary[] | null
  outboundDeliveries: WorkOrderWorkspaceOutboundDeliverySummary[] | null
  nextCursor: string | null
}

export type OutboundReviewSubmission = {
  deliveryId: string
  sourceReviewCaseId: string
  sourceWorkOrderId: string
  status: 'PENDING' | 'SENDING' | 'DELIVERED' | 'ACKNOWLEDGED' | 'REJECTED' | 'FAILED_FINAL' | 'UNKNOWN'
  aggregateVersion: number
}

/** 工单工作区区块快照；只有被请求的那个 section 字段非空。 */
export type WorkspaceSection = {
  section:
    | 'TASKS'
    | 'TIMELINE_AUDIT'
    | 'APPOINTMENTS_VISITS'
    | 'FORMS_EVIDENCE'
    | 'REVIEWS_CORRECTIONS'
    | 'INTEGRATION'
  sourceVersions: { workOrderVersion: number }
  meta: { asOf: string; freshnessStatus: string }
  tasks: { items: WorkOrderWorkspaceTask[]; nextCursor: string | null } | null
  timeline: WorkOrderWorkspaceTimelineSectionData | null
  appointmentsVisits: WorkOrderWorkspaceAppointmentsVisitsSectionData | null
  formsEvidence: WorkOrderWorkspaceFormsEvidenceSectionData | null
  reviewsCorrections: WorkOrderWorkspaceReviewsCorrectionsSectionData | null
  integration: WorkOrderWorkspaceIntegrationSectionData | null
}

export type WorkbenchView = {
  priorityCount: number
  reviewCount: number
  correctionCount: number
  dispatchCount: number
  slaRiskCount: number
  exceptionCount: number
  waitingExternalCount: number
  unassignedCount: number
  generatedAt: string
}

export type AdminWorkOrderDirectoryItem = {
  id: string
  orderCode: string
  customerName: string | null
  customerPhone: string | null
  projectId: string
  projectName: string | null
  clientName: string | null
  serviceName: string | null
  stageName: string | null
  networkName: string | null
  technicianName: string | null
  slaLevel: 'NORMAL' | 'RISK' | 'BREACHED'
  slaLabel: string
  statusName: string | null
  updatedAt: string
  dataComplete: boolean
  dataProblem: string | null
}

export type AdminWorkOrderDirectoryView = {
  items: AdminWorkOrderDirectoryItem[]
  projectOptions: Array<{ id: string; name: string }>
  queueSummary: WorkbenchView
  nextCursor: string | null
  totalCount: number
  generatedAt: string
}

export type TaskAllowedAction = {
  code: string
  label: string
  inputSchemaRef: string | null
  obligations: string[]
}

export type AdminWorkOrderWorkspaceView = {
  workspace: WorkOrderWorkspace
  projectName: string | null
  clientName: string | null
  serviceName: string | null
  stageName: string | null
  taskName: string | null
  statusName: string | null
  allowedActions: TaskAllowedAction[]
  blockedActions: Array<{ code: string; label: string; reason: string }>
  dataComplete: boolean
  dataProblem: string | null
  generatedAt: string
}

export type NetworkAssignmentCandidate = {
  networkId: string
  networkName: string
  rank: number
  coverageSummary: string
  remainingCapacity: number
  recommendationSummary: string
}

export type NetworkAssignmentCandidateView = {
  taskId: string
  workOrderId: string
  businessType: string
  generatedAt: string
  rankingExplanation: string
  emptyReason: string | null
  candidates: NetworkAssignmentCandidate[]
}

export function loadAdminWorkbench() {
  return get<WorkbenchView>('/admin/workbench').then((result) => result.data)
}

export function loadAdminWorkOrders(query: Record<string, string | number | boolean | undefined>) {
  return get<AdminWorkOrderDirectoryView>('/admin/work-orders', query).then((result) => result.data)
}

export function loadAdminWorkOrderWorkspace(workOrderId: string) {
  return get<AdminWorkOrderWorkspaceView>(`/admin/work-orders/${workOrderId}/workspace`).then(
    (result) => result.data,
  )
}

export function loadWorkspaceSection(workOrderId: string, section: string) {
  return get<WorkspaceSection>(`/work-orders/${workOrderId}/workspace/sections/${section}`).then(
    (result) => result.data,
  )
}

/** 将已通过的平台审核单提交车企；服务端按 sourceReviewCase 幂等复用既有 Delivery。 */
export function createBydReviewSubmission(sourceReviewCaseId: string) {
  return post<OutboundReviewSubmission>(
    '/internal/integration/byd/review-submissions',
    { sourceReviewCaseId },
    { 'Idempotency-Key': newIdempotencyKey('submit-byd-review') },
  ).then((result) => result.data)
}

export function loadNetworkCandidates(taskId: string) {
  return get<NetworkAssignmentCandidateView>(`/tasks/${taskId}/network-assignment-candidates`).then(
    (result) => result.data,
  )
}

export function assignNetwork(
  taskId: string,
  input: { networkAssigneeId: string; businessType: string },
) {
  return post(`/tasks/${taskId}/service-assignments:manual-assign-network`, input, {
    'Idempotency-Key': newIdempotencyKey('manual-assign-network'),
  })
}

export type HumanTaskCommandReceipt = {
  taskId: string
  status: 'CLAIMED' | 'RUNNING' | 'COMPLETED'
  actorId: string
  version: number
  occurredAt: string
}

/**
 * 人工任务命令统一使用「幂等键 + If-Match 强 ETag（双引号包裹的正整数版本）」，
 * 陈旧版本或并发操作由后端以 409/412 失败关闭，前端不得静默重试。
 */
function taskCommandHeaders(prefix: string, version: number): Record<string, string> {
  return {
    'Idempotency-Key': newIdempotencyKey(prefix),
    'If-Match': `"${version}"`,
  }
}

/** 领取 READY 人工任务；契约无请求体。 */
export function claimTask(taskId: string, version: number) {
  return post<HumanTaskCommandReceipt>(
    `/tasks/${taskId}:claim`,
    undefined,
    taskCommandHeaders('claim-task', version),
  ).then((result) => result.data)
}

/** 当前领取人启动人工任务；契约无请求体。 */
export function startTask(taskId: string, version: number) {
  return post<HumanTaskCommandReceipt>(
    `/tasks/${taskId}:start`,
    undefined,
    taskCommandHeaders('start-task', version),
  ).then((result) => result.data)
}

/** 当前责任人在 start 前释放已领取任务，必须给出释放原因码。 */
export function releaseTask(taskId: string, version: number, reasonCode: string) {
  return post<HumanTaskCommandReceipt>(
    `/tasks/${taskId}:release`,
    { reasonCode },
    taskCommandHeaders('release-task', version),
  ).then((result) => result.data)
}

/** 当前执行人完成人工任务；resultDigest 必须为 resultRef 的 sha256 十六进制（表单/资料 Task 另有约定）。 */
export function completeTask(
  taskId: string,
  version: number,
  input: { resultRef: string; resultDigest: string },
) {
  return post<HumanTaskCommandReceipt>(
    `/tasks/${taskId}:complete`,
    input,
    taskCommandHeaders('complete-task', version),
  ).then((result) => result.data)
}
