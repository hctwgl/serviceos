import { apiPost, newIdempotencyKey } from './client'

export type ManualAssignServiceAssignmentRequest = {
  networkAssigneeId: string
  technicianAssigneeId: string
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
