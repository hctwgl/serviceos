import { apiGet, apiPost, newIdempotencyKey } from './client'

export type TaskForm = {
  taskId: string
  formVersionId: string
  formKey: string
  semanticVersion: string
  schemaVersion: string
  definition: Record<string, unknown>
  contentDigest: string
}

export type FormSubmission = {
  submissionId: string
  taskId: string
  projectId: string
  formVersionId: string
  formKey: string
  submissionVersion: number
  values: Record<string, unknown>
  contentDigest: string
  validationStatus: 'VALIDATED' | 'INVALID'
  errors: Array<{ fieldKey: string; code: string; message: string }>
  warnings: Array<{ fieldKey: string; code: string; message: string }>
  submittedBy: string
  submittedAt: string
}

export type EvidenceSlot = {
  slotId: string
  taskId: string
  projectId: string
  requirementCode: string
  requirementName: string
  mediaType: string
  required: boolean
  status: string
  active: boolean
  requiredDisposition: string | null
  resolutionId: string
}

export type EvidenceRevision = {
  evidenceRevisionId: string
  revisionNumber: number
  status: string
  contentDigest: string
}

export type EvidenceItem = {
  evidenceItemId: string
  taskId: string
  projectId: string
  evidenceSlotId: string
  itemOrdinal: number
  status: string
  revisions: EvidenceRevision[]
}

export type EvidenceSetSnapshot = {
  evidenceSetSnapshotId: string
  taskId: string
  projectId: string
  purpose: 'TASK_SUBMISSION'
  contentDigest: string
  memberCount: number
  createdAt: string
}

export function listTaskForms(taskId: string) {
  return apiGet<TaskForm[]>(`/tasks/${taskId}/forms`)
}

export function submitTaskForm(
  taskId: string,
  body: { formVersionId: string; values: Record<string, unknown>; prefillVersion?: string | null },
) {
  return apiPost<FormSubmission>(`/tasks/${taskId}/form-submissions`, {
    idempotencyKey: newIdempotencyKey('form-submit'),
    body,
  })
}

export function listTaskEvidenceSlots(taskId: string) {
  return apiGet<EvidenceSlot[]>(`/tasks/${taskId}/evidence-slots`)
}

export function listTaskEvidenceItems(taskId: string) {
  return apiGet<EvidenceItem[]>(`/tasks/${taskId}/evidence-items`)
}

export function createEvidenceSetSnapshot(taskId: string, memberRevisionIds: string[]) {
  return apiPost<EvidenceSetSnapshot>(`/tasks/${taskId}/evidence-set-snapshots`, {
    idempotencyKey: newIdempotencyKey('evidence-snapshot'),
    body: { purpose: 'TASK_SUBMISSION', memberRevisionIds },
  })
}
