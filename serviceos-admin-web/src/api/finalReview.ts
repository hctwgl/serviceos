import { apiGet, apiPost } from './client'

export type FinalReviewGateCheck = {
  code: string
  label: string
  status: 'PASS' | 'FAIL' | 'WARN' | 'PENDING' | 'NOT_APPLICABLE'
  blocking: boolean
  detail?: string | null
}

export type FinalReviewTarget = {
  targetType: 'EvidenceRevision'
  targetId: string
  targetVersion: number
  requirementCode: string
  requirementLabel: string
  requirementDescription?: string | null
  groupCode: string
  groupLabel: string
  displayOrder: number
  required: boolean
  slotId: string
  evidenceItemId: string
  revisionId: string
  revisionNo: number
  mimeType?: string | null
  lifecycleStatus: string
  capturedAt?: string | null
  captureSource?: string | null
  uploaderDisplayName?: string | null
  offline?: boolean | null
  locationVerdict?: string | null
  validationReadiness: string
  validationResult?: string | null
  validationCodes: string[]
  validationMessages: string[]
  structuredValues: Record<string, unknown>
}

export type FinalReviewTargetGroup = {
  groupCode: string
  groupLabel: string
  displayOrder: number
  targets: FinalReviewTarget[]
}

export type FinalReviewAllowedAction = {
  action: 'DECIDE' | 'PREVIEW_EVIDENCE' | 'OPEN_CORRECTION' | 'VIEW_ONLY'
  enabled: boolean
  reason?: string | null
}

export type FinalReviewWorkspaceSection = {
  data: {
    workOrder: {
      workOrderId: string
      displayNo: string
      projectId: string
      projectName?: string | null
      statusCode: string
      statusLabel: string
      serviceProductCode: string
      serviceProductName?: string | null
      maskedCustomerName?: string | null
      maskedCustomerPhone?: string | null
      maskedServiceAddress?: string | null
      networkName?: string | null
      technicianName?: string | null
      deviceModel?: string | null
      nextActionLabel: string
    }
    reviewTask: {
      taskId: string
      status: string
      statusLabel: string
      assigneeDisplayName?: string | null
      resourceVersion: number
      executionGuarded: boolean
    } | null
    reviewCase: {
      reviewCaseId: string
      origin: 'INTERNAL' | 'CLIENT'
      status: string
      aggregateVersion: number
      snapshotId: string
      snapshotDigest: string
      policyVersionId: string
      targetCount: number
    } | null
    sla: {
      status: string
      startedAt?: string | null
      dueAt?: string | null
      displayText: string
    } | null
    gateChecks: FinalReviewGateCheck[]
    targetGroups: FinalReviewTargetGroup[]
    rejectionReasons: Array<{ code: string; label: string; requiresNote: boolean }>
    allowedActions: FinalReviewAllowedAction[]
    defaultTargetRef: { targetType: 'EvidenceRevision'; targetId: string } | null
  }
  meta: {
    asOf: string
    projectionCheckpoint: string
    freshnessStatus: string
    scopeVersion: number
    queryId: string
  }
}

export type DownloadAuthorization = {
  authorizationId: string
  fileId: string
  method: string
  downloadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}

export function getFinalReviewWorkspaceSection(workOrderId: string) {
  return apiGet<FinalReviewWorkspaceSection>(
    `/work-orders/${workOrderId}/workspace/sections/FINAL_REVIEW`,
  )
}

export async function authorizeEvidenceRevisionDownload(revisionId: string, purpose: string) {
  const result = await apiPost<DownloadAuthorization>(
    `/evidence-revisions/${revisionId}/download-authorizations`,
    { body: { purpose } },
  )
  return result.data
}
