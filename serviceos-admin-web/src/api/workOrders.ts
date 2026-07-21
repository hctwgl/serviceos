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
  /** M435：工单聚合独立更新时间；目录「更新时间」列使用本字段。 */
  updatedAt: string
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
  /** M439：ACTIVE NETWORK 责任 ID；无责任为 null。 */
  currentNetworkId: string | null
  /** M439：网点显示名。 */
  currentNetworkDisplayName: string | null
  /** M439：ACTIVE TECHNICIAN 师傅档案 ID。 */
  currentTechnicianId: string | null
  /** M439：师傅显示名。 */
  currentTechnicianDisplayName: string | null
}

/** M434：页级 SLA 风险摘要；缺 sla.read 时响应省略本字段。 */
export type WorkOrderDirectorySlaRiskSummary = {
  workOrderId: string
  openCount: number
  breachedCount: number
}

export type WorkOrderPage = {
  items: WorkOrder[]
  nextCursor: string | null
  asOf: string
  /** M444：当前筛选授权精确总数。 */
  totalCount: number
  /** M444：目录路径恒为 false（兼容字段）。 */
  totalCountTruncated: boolean
  /** soft-omit：缺 PROJECT sla.read 时为 undefined */
  slaRiskSummaries?: WorkOrderDirectorySlaRiskSummary[] | null
}

export function listAuthorizedWorkOrders(query: Record<string, string | undefined> = {}) {
  return apiGet<WorkOrderPage>('/work-orders', query)
}
