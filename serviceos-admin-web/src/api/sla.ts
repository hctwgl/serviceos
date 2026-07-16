import { apiGet } from './client'

export type SlaInstanceItem = {
  slaInstanceId: string
  projectId: string
  workOrderId: string
  taskId: string
  slaRef: string
  status: 'RUNNING' | 'BREACHED' | 'MET' | 'MET_LATE'
  deadlineAt: string
  startedAt: string
  remainingSeconds: number
  overdueSeconds: number
  elapsedSeconds: number
  aggregateVersion: number
}

export type SlaInstancePage = {
  items: SlaInstanceItem[]
  nextCursor: string | null
  asOf: string
}

export function listSlaInstances(query: Record<string, string | undefined> = {}) {
  return apiGet<SlaInstancePage>('/sla-instances', query)
}
