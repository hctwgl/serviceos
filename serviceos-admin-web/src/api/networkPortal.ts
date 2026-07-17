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

export type NetworkPortalAppointment = {
  appointmentId: string
  taskId: string
  type: string
  status: string
  assignedNetworkId: string | null
  aggregateVersion: number
  currentRevisionNo: number
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

export function isPortalContextInvalid(err: unknown): boolean {
  const problem = (err as HttpStatusError | undefined)?.problem
  return (
    (err as HttpStatusError | undefined)?.status === 403 &&
    (problem?.errorCode === 'PORTAL_CONTEXT_INVALID' ||
      problem?.code === 'PORTAL_CONTEXT_INVALID' ||
      problem?.title === 'PORTAL_CONTEXT_INVALID')
  )
}
