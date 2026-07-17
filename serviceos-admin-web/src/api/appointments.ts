import { apiGet, apiGetWithMeta, apiPost, newIdempotencyKey, quotedVersion } from './client'

export type AppointmentWindow = {
  start: string
  end: string
  timezone: string
  estimatedDurationMinutes: number
}

export type AppointmentRevision = {
  revisionId: string
  revisionNo: number
  windowStart?: string | null
  windowEnd?: string | null
  timezone?: string | null
  estimatedDurationMinutes?: number | null
  addressRef?: string | null
  addressVersion?: string | null
  status?: string | null
  confirmedPartyType?: string | null
  confirmedPartyRef?: string | null
  confirmationChannel?: string | null
  createdAt?: string | null
}

export type Appointment = {
  appointmentId: string
  projectId: string
  workOrderId: string
  taskId: string
  type: string
  status: string
  assignedNetworkId: string | null
  technicianId: string | null
  aggregateVersion: number
  currentRevisionNo: number
  createdAt: string
  createdBy: string
  revisions?: AppointmentRevision[]
  allowedActions: Array<'CONFIRM' | 'RESCHEDULE' | 'CANCEL' | 'MARK_NO_SHOW'>
}

export type AppointmentCommandReceipt = {
  appointmentId: string
  revisionId: string
  status: string
  revisionNo: number
  aggregateVersion: number
  occurredAt: string
}

export type ContactAttempt = {
  contactAttemptId: string
  projectId: string
  workOrderId: string
  taskId: string
  channel: string
  contactedPartyRef: string
  startedAt: string
  endedAt: string
  resultCode: string
  note: string | null
  nextContactAt: string | null
  recordingRef: string | null
  actorId: string
  createdAt: string
}

export type VisitLocation = {
  latitude: number
  longitude: number
  accuracyMeters: number
}

export type Visit = {
  visitId: string
  projectId: string
  workOrderId: string
  taskId: string
  appointmentId: string
  visitSequence: number
  technicianId: string
  networkId?: string | null
  status: string
  checkInCapturedAt?: string
  checkInReceivedAt?: string
  checkInLocation?: VisitLocation | null
  geofenceResult?: string | null
  geofenceDistanceMeters?: number | null
  geofencePolicyVersion?: string | null
  policyDecision?: string | null
  deviceId?: string | null
  deviceCommandId?: string | null
  offline?: boolean
  checkOutCapturedAt?: string | null
  checkOutReceivedAt?: string | null
  resultCode?: string | null
  exceptionCode?: string | null
  note?: string | null
  operationRefs?: string[]
  evidenceRefs?: string[]
  aggregateVersion: number
  allowedActions?: Array<'visit.checkOut' | 'visit.interrupt' | 'CHECK_OUT' | 'INTERRUPT'>
}

export type VisitCommandReceipt = {
  visitId: string
  status: string
  aggregateVersion: number
  geofenceResult: string
  policyDecision: string
  occurredAt: string
}

export function listTaskAppointments(taskId: string) {
  return apiGet<Appointment[]>(`/tasks/${taskId}/appointments`)
}

export function listTaskContactAttempts(taskId: string) {
  return apiGet<ContactAttempt[]>(`/tasks/${taskId}/contact-attempts`)
}

export function getAppointment(appointmentId: string) {
  return apiGetWithMeta<Appointment>(`/appointments/${appointmentId}`)
}

export function getVisit(visitId: string) {
  return apiGetWithMeta<Visit>(`/visits/${visitId}`)
}

export function proposeAppointment(
  taskId: string,
  body: {
    type: 'SURVEY' | 'INSTALLATION' | 'REPAIR' | 'CORRECTION' | 'SECOND_VISIT'
    window: AppointmentWindow
    addressRef: string
    addressVersion: string
  },
) {
  return apiPost<AppointmentCommandReceipt>(`/tasks/${taskId}/appointments`, {
    idempotencyKey: newIdempotencyKey('appt-propose'),
    body,
  })
}

export function confirmAppointment(
  appointmentId: string,
  aggregateVersion: number,
  body: {
    confirmedPartyType: string
    confirmedPartyRef: string
    confirmationChannel: string
  },
) {
  return apiPost<AppointmentCommandReceipt>(`/appointments/${appointmentId}:confirm`, {
    idempotencyKey: newIdempotencyKey('appt-confirm'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function cancelAppointment(
  appointmentId: string,
  aggregateVersion: number,
  body: { reasonCode: string; note?: string | null },
) {
  return apiPost<AppointmentCommandReceipt>(`/appointments/${appointmentId}:cancel`, {
    idempotencyKey: newIdempotencyKey('appt-cancel'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function rescheduleAppointment(
  appointmentId: string,
  aggregateVersion: number,
  body: { newWindow: AppointmentWindow; reasonCode: string; note?: string | null },
) {
  return apiPost<AppointmentCommandReceipt>(`/appointments/${appointmentId}:reschedule`, {
    idempotencyKey: newIdempotencyKey('appt-reschedule'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function markAppointmentNoShow(
  appointmentId: string,
  aggregateVersion: number,
  body: {
    noShowPartyType: string
    noShowPartyRef: string
    reasonCode: string
    evidenceRefs: string[]
  },
) {
  return apiPost<AppointmentCommandReceipt>(`/appointments/${appointmentId}:mark-no-show`, {
    idempotencyKey: newIdempotencyKey('appt-noshow'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function recordTaskContactAttempt(
  taskId: string,
  body: {
    channel: string
    contactedPartyRef: string
    startedAt: string
    endedAt: string
    resultCode:
      | 'CONNECTED'
      | 'NO_ANSWER'
      | 'BUSY'
      | 'WRONG_NUMBER'
      | 'USER_REQUESTED_LATER'
      | 'INVALID_CONTACT'
    note?: string | null
  },
) {
  return apiPost<ContactAttempt>(`/tasks/${taskId}/contact-attempts`, {
    idempotencyKey: newIdempotencyKey('contact'),
    body,
  })
}

export function checkInVisit(
  appointmentId: string,
  body: {
    capturedAt: string
    deviceCommandId: string
    deviceId: string
    location: { latitude: number; longitude: number; accuracyMeters: number }
    offline: boolean
  },
) {
  return apiPost<VisitCommandReceipt>(`/appointments/${appointmentId}/visits:check-in`, {
    idempotencyKey: body.deviceCommandId,
    body,
  })
}

export function checkOutVisit(
  visitId: string,
  aggregateVersion: number,
  body: {
    capturedAt: string
    resultCode: string
    operationRefs: string[]
  },
) {
  return apiPost<VisitCommandReceipt>(`/visits/${visitId}:check-out`, {
    idempotencyKey: newIdempotencyKey('visit-checkout'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function interruptVisit(
  visitId: string,
  aggregateVersion: number,
  body: {
    capturedAt: string
    exceptionCode: string
    note?: string | null
    evidenceRefs: string[]
  },
) {
  return apiPost<VisitCommandReceipt>(`/visits/${visitId}:interrupt`, {
    idempotencyKey: newIdempotencyKey('visit-interrupt'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function listWorkOrderVisits(workOrderId: string) {
  return apiGet<Visit[]>(`/work-orders/${workOrderId}/visits`)
}
