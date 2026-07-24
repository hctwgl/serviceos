import type {
  AdminProjectFulfillmentProfile,
  AdminProjectWorkspaceView,
  ProjectFulfillmentStageDraft,
} from '@serviceos/api-client'
import type { AdminWorkOrderDirectoryItem, AdminWorkOrderDirectoryView } from '@serviceos/api-client'
import { formatDateTime } from '../../../presenters/work-order'
import type { ProjectStageBarItem, RiskPanelItem } from '../../../components/serviceos/types'

const projectStatusLabels: Record<AdminProjectWorkspaceView['status'], { label: string; tone: 'green' | 'blue' | 'orange' | 'gray' }> = {
  DRAFT: { label: '准备中', tone: 'blue' },
  ACTIVE: { label: '履约中', tone: 'green' },
  SUSPENDED: { label: '已暂停', tone: 'orange' },
  CLOSED: { label: '已结束', tone: 'gray' },
}

const serviceProductLabels: Record<string, string> = {
  HOME_CHARGING_SURVEY_INSTALL: '家充勘测安装服务',
  HOME_CHARGER_INSTALLATION: '充电桩安装服务',
  HOME_CHARGER_SURVEY: '家充勘测服务',
  CHARGER_REPAIR: '充电桩维修服务',
}

const workflowOwnerLabels: Record<string, string> = {
  NETWORK: '责任网点',
  PLATFORM: '平台运营',
  SYSTEM: '系统自动执行',
  TECHNICIAN: '责任师傅',
}

const taskLabels: Record<string, string> = {
  ASSIGN_COORDINATORS: '责任分配',
  DISPATCH: '派单协调',
  FIELD_INSTALL: '现场安装',
  FIELD_SURVEY: '现场勘测',
  INSTALL: '现场安装',
  REVIEW: '资料审核',
  REVIEW_TASK: '资料审核',
  SURVEY: '现场勘测',
}

export function presentAdminProjectStatus(status: AdminProjectWorkspaceView['status']) {
  return projectStatusLabels[status] ?? { label: '状态待确认', tone: 'gray' as const }
}

export function presentServiceProduct(code: string | null | undefined) {
  if (!code) return '服务产品待确认'
  return serviceProductLabels[code] ?? '项目服务产品'
}

export function presentWorkflowOwner(ownerType: string | null | undefined) {
  if (!ownerType) return '责任待确认'
  return workflowOwnerLabels[ownerType] ?? '责任待确认'
}

export function presentTaskType(taskType: string | null | undefined) {
  if (!taskType) return '等待业务事件'
  return taskLabels[taskType] ?? '业务任务'
}

function splitWorkflowSummary(summary: string | null | undefined) {
  if (!summary) return []
  return summary
    .split(/\s*(?:→|->|＞|>|\/|、|，|,)\s*/)
    .map((item) => item.replace(/^第\s*\d+\s*阶段[:：]?\s*/, '').trim())
    .filter((item) => item.length > 0 && item.length < 36)
}

function workflowStageNames(profile: AdminProjectFulfillmentProfile | undefined) {
  const fromSummary = splitWorkflowSummary(profile?.workflowSummary)
  if (fromSummary.length) return fromSummary
  const count = Math.max(0, profile?.stageCount ?? 0)
  return Array.from({ length: count }, (_, index) => `履约阶段 ${String(index + 1).padStart(2, '0')}`)
}

function matchingStageIndex(names: string[], currentStageName: string | null | undefined) {
  if (!currentStageName) return -1
  const normalized = currentStageName.replace(/\s/g, '')
  return names.findIndex((name) => {
    const candidate = name.replace(/\s/g, '')
    return candidate === normalized || candidate.includes(normalized) || normalized.includes(candidate)
  })
}

export function buildProjectStageBar(
  project: AdminProjectWorkspaceView | undefined,
  profiles: AdminProjectFulfillmentProfile[],
  workOrders: AdminWorkOrderDirectoryView | undefined,
  fulfillmentStages: ProjectFulfillmentStageDraft[] = [],
): ProjectStageBarItem[] {
  const profile = profiles.find((item) => item.status === 'ACTIVE') ?? profiles[0]
  const names = fulfillmentStages.length
    ? fulfillmentStages
        .slice()
        .sort((left, right) => left.sequence - right.sequence)
        .map((stage) => stage.stageName)
        .filter(Boolean)
    : workflowStageNames(profile)
  if (!names.length) return []

  const currentStageName = workOrders?.items.find((item) => item.stageName)?.stageName
  const currentIndex = matchingStageIndex(names, currentStageName)
  const hasActiveWork = (project?.activeWorkOrderCount ?? 0) > 0 || Boolean(workOrders?.items.length)

  return names.map((name, index) => ({
    code: `project-stage-${index + 1}`,
    label: name,
    status: !hasActiveWork
      ? 'pending'
      : currentIndex >= 0
        ? index < currentIndex ? 'completed' : index === currentIndex ? 'current' : 'pending'
        : index === 0 ? 'current' : 'pending',
    detail: currentIndex < 0 && index === 0 && hasActiveWork ? '当前工单投影未提供阶段名称' : undefined,
  }))
}

export function buildProjectRiskItems(
  workOrders: AdminWorkOrderDirectoryView | undefined,
): RiskPanelItem[] {
  const summary = workOrders?.queueSummary
  if (!summary) return []
  const items: RiskPanelItem[] = []
  if (summary.slaRiskCount > 0) {
    items.push({
      key: 'sla-risk',
      label: 'SLA 风险工单',
      count: summary.slaRiskCount,
      description: '需要项目经理关注时效承诺',
      tone: 'danger',
      to: '/work-orders?view=priority',
    })
  }
  if (summary.exceptionCount > 0) {
    items.push({
      key: 'exception',
      label: '质量与履约异常',
      count: summary.exceptionCount,
      description: '进入工单工作区查看处置入口',
      tone: 'warning',
      to: '/work-orders?view=exception',
    })
  }
  if (summary.unassignedCount > 0) {
    items.push({
      key: 'unassigned',
      label: '责任待分配',
      count: summary.unassignedCount,
      description: '责任网点或当前任务尚未确认',
      tone: 'warning',
      to: '/work-orders?view=dispatch',
    })
  }
  if (summary.waitingExternalCount > 0) {
    items.push({
      key: 'external',
      label: '等待外部处理',
      count: summary.waitingExternalCount,
      description: '存在车企回传或外部确认等待',
      tone: 'neutral',
      to: '/work-orders?view=external',
    })
  }
  return items
}

export type ProjectActivityItem = {
  id: string
  time: string
  label: string
  detail: string
  tone: 'blue' | 'green' | 'orange'
}

function activityTone(item: AdminWorkOrderDirectoryItem): ProjectActivityItem['tone'] {
  if (item.slaLevel === 'BREACHED') return 'orange'
  if (item.statusName === '已完成') return 'green'
  return 'blue'
}

export function buildProjectActivities(items: AdminWorkOrderDirectoryItem[]): ProjectActivityItem[] {
  return [...items]
    .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
    .slice(0, 5)
    .map((item) => ({
      id: item.id,
      time: formatDateTime(item.updatedAt),
      label: item.stageName ? `${item.stageName}工单动态` : '工单数据更新',
      detail: `${item.orderCode} · ${item.statusName ?? '状态待确认'}`,
      tone: activityTone(item),
    }))
}
