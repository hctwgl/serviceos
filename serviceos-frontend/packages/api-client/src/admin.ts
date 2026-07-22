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
    currentAssigneeDisplayName: string | null
    currentNetworkDisplayName: string | null
    currentTechnicianDisplayName: string | null
  }
  currentTaskSummary: WorkOrderWorkspaceTask | null
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

export type WorkspaceSection = {
  section: string
  availability: string
  data: Record<string, unknown> | null
  tasks?: { items: WorkOrderWorkspaceTask[]; nextCursor: string | null } | null
  timeline?: {
    items: Array<{ eventType: string; occurredAt: string }>
    nextCursor: string | null
  } | null
  formsEvidence?: Record<string, unknown> | null
  reviewsCorrections?: Record<string, unknown> | null
  integration?: Record<string, unknown> | null
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
