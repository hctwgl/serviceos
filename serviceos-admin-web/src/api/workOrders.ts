import { apiGet } from './client'

export type WorkOrder = {
  id: string
  projectId: string
  clientCode: string
  brandCode: string
  serviceProductCode: string
  externalOrderCode: string
  status: 'RECEIVED' | 'ACTIVE' | 'FULFILLED'
  receivedAt: string
  version: number
}

export type WorkOrderPage = {
  items: WorkOrder[]
  nextCursor: string | null
  asOf: string
}

export function listAuthorizedWorkOrders(query: Record<string, string | undefined> = {}) {
  return apiGet<WorkOrderPage>('/work-orders', query)
}
