import { apiPost, newIdempotencyKey } from './client'

export type ManualAssignServiceAssignmentRequest = {
  networkAssigneeId: string
  technicianAssigneeId: string
  businessType: string
}

export type ManualAssignNetworkRequest = {
  networkAssigneeId: string
  businessType: string
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

export type ManualAssignNetworkReceipt = {
  taskId: string
  workOrderId: string
  networkServiceAssignmentId: string
  networkAssigneeId: string
  occurredAt: string
}

/** Admin 人工初派：同事务激活 NETWORK + TECHNICIAN ACTIVE 责任。 */
export function manualAssignServiceAssignments(
  taskId: string,
  body: ManualAssignServiceAssignmentRequest,
) {
  return apiPost<ManualServiceAssignmentReceipt>(
    `/tasks/${taskId}/service-assignments:manual-assign`,
    {
      idempotencyKey: newIdempotencyKey('manual-assign'),
      body,
    },
  )
}

/** Admin 初审派网点：仅激活 ACTIVE NETWORK，不强制师傅。 */
export function manualAssignNetworkServiceAssignment(
  taskId: string,
  body: ManualAssignNetworkRequest,
) {
  return apiPost<ManualAssignNetworkReceipt>(
    `/tasks/${taskId}/service-assignments:manual-assign-network`,
    {
      idempotencyKey: newIdempotencyKey('manual-assign-network'),
      body,
    },
  )
}
