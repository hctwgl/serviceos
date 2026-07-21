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
  /** M429：服务端脱敏客户联系。 */
  maskedCustomerName: string | null
  maskedCustomerPhone: string | null
  maskedServiceAddress: string | null
}

export type WorkOrderPage = {
  items: WorkOrder[]
  nextCursor: string | null
  asOf: string
}

export function listAuthorizedWorkOrders(query: Record<string, string | undefined> = {}) {
  return apiGet<WorkOrderPage>('/work-orders', query)
}
