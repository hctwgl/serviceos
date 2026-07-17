/** Network Portal API：networkId 只经 X-Network-Context，禁止 query-param。 */
import { apiGet, apiPost, type HttpStatusError } from './client'

export type NetworkPortalPage<T> = {
  networkId: string
  items: T[]
  asOf: string
}

export type NetworkPortalWorkOrderItem = {
  workOrderId: string
  projectId: string | null
  taskIds: string[]
  businessType: string | null
  technicianId: string | null
  effectiveFrom: string | null
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
}

export type NetworkPortalTechnicianItem = {
  membershipId: string
  technicianProfileId: string
  principalId: string
  displayName: string
  profileStatus: string
  membershipStatus: string
  validFrom: string
  validTo: string | null
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

export function getNetworkPortalWorkbench(networkContextId: string) {
  return apiGet<NetworkPortalWorkbench>(
    '/network-portal/workbench',
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

export function isPortalContextInvalid(err: unknown): boolean {
  const problem = (err as HttpStatusError | undefined)?.problem
  return (
    (err as HttpStatusError | undefined)?.status === 403 &&
    (problem?.errorCode === 'PORTAL_CONTEXT_INVALID' ||
      problem?.code === 'PORTAL_CONTEXT_INVALID' ||
      problem?.title === 'PORTAL_CONTEXT_INVALID')
  )
}
