import { apiGetWithMeta, apiPost, newIdempotencyKey, quotedVersion, type ApiResult } from './client'

export type TaskAllowedAction = {
  code: 'task.claim' | 'task.start' | 'task.complete' | 'task.release'
  label: string
  inputSchemaRef: string | null
  obligations: Array<'REQUIRE_REASON' | 'REQUIRE_RESULT'>
}

export type TaskAllowedActions = {
  resourceVersion: number
  asOf: string
  actions: TaskAllowedAction[]
}

export type HumanTaskCommandReceipt = {
  taskId: string
  status: string
  actorId: string
  version: number
  occurredAt: string
}

export type CompleteHumanTaskRequest = {
  resultRef: string
  resultDigest: string
  inputVersionRefs?: Array<{
    kind: 'FORM_SUBMISSION' | 'EVIDENCE_SET_SNAPSHOT'
    ref: string
    digest: string
  }>
}

export type ReleaseHumanTaskRequest = {
  reasonCode: string
}

/** 只读取服务端投影；前端不得本地推导可执行动作。 */
export async function getTaskAllowedActions(taskId: string): Promise<ApiResult<TaskAllowedActions>> {
  return apiGetWithMeta<TaskAllowedActions>(`/tasks/${taskId}/allowed-actions`)
}

export function claimHumanTask(taskId: string, resourceVersion: number) {
  return apiPost<HumanTaskCommandReceipt>(`/tasks/${taskId}:claim`, {
    idempotencyKey: newIdempotencyKey('claim'),
    ifMatch: quotedVersion(resourceVersion),
  })
}

export function startHumanTask(taskId: string, resourceVersion: number) {
  return apiPost<HumanTaskCommandReceipt>(`/tasks/${taskId}:start`, {
    idempotencyKey: newIdempotencyKey('start'),
    ifMatch: quotedVersion(resourceVersion),
  })
}

export function completeHumanTask(
  taskId: string,
  resourceVersion: number,
  body: CompleteHumanTaskRequest,
) {
  return apiPost<HumanTaskCommandReceipt>(`/tasks/${taskId}:complete`, {
    idempotencyKey: newIdempotencyKey('complete'),
    ifMatch: quotedVersion(resourceVersion),
    body,
  })
}

export function releaseHumanTask(
  taskId: string,
  resourceVersion: number,
  body: ReleaseHumanTaskRequest,
) {
  return apiPost<HumanTaskCommandReceipt>(`/tasks/${taskId}:release`, {
    idempotencyKey: newIdempotencyKey('release'),
    ifMatch: quotedVersion(resourceVersion),
    body,
  })
}

export type TaskAssignmentBatchReceipt = {
  assignmentBatchId: string
  taskId: string
  candidateCount: number
  taskVersion: number
  assignedAt: string
}

export function assignTaskCandidates(
  taskId: string,
  resourceVersion: number,
  body: {
    candidatePrincipalIds: string[]
    sourceType: 'ASSIGNEE_POLICY' | 'MANUAL'
    sourceId: string
  },
) {
  return apiPost<TaskAssignmentBatchReceipt>(`/tasks/${taskId}:assign-candidates`, {
    idempotencyKey: newIdempotencyKey('assign'),
    ifMatch: quotedVersion(resourceVersion),
    body,
  })
}
