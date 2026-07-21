import { apiGet } from './client'

export type WorkOrder = {
  id: string
  projectId: string
  clientCode: string
  brandCode: string
  serviceProductCode: string
  externalOrderCode: string
  status: 'RECEIVED' | 'ACTIVE' | 'FULFILLED'
  /** M430：服务区域国标码（目录列展示；名称解析仍可后续增强）。 */
  provinceCode: string
  cityCode: string
  districtCode: string
  receivedAt: string
  version: number
  /** M429：服务端脱敏客户联系。 */
  maskedCustomerName: string | null
  maskedCustomerPhone: string | null
  maskedServiceAddress: string | null
  /** M432：当前阶段码；无 ACTIVE 任务时为 null。 */
  currentStageCode: string | null
  /** M433：当前 ACTIVE 任务认领主体；未认领为 null。 */
  currentClaimedBy: string | null
  /** M433：Persona 显示名；无档案为 null，不发明名称。 */
  currentAssigneeDisplayName: string | null
}

export type WorkOrderPage = {
  items: WorkOrder[]
  nextCursor: string | null
  asOf: string
}

export function listAuthorizedWorkOrders(query: Record<string, string | undefined> = {}) {
  return apiGet<WorkOrderPage>('/work-orders', query)
}
