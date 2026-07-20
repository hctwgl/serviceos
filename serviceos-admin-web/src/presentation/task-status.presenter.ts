import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  READY: {
    label: '待开始',
    semantic: 'warning',
    icon: 'clock',
    description: '任务已就绪，等待开始',
  },
  PENDING: {
    label: '待处理',
    semantic: 'warning',
    icon: 'clock',
    description: '任务等待处理',
  },
  UNASSIGNED: {
    label: '待分配',
    semantic: 'warning',
    icon: 'warning',
    description: '尚未分配责任方',
  },
  ASSIGNED: {
    label: '已分配',
    semantic: 'info',
    icon: 'info',
    description: '责任方已确定',
  },
  CLAIMED: {
    label: '已认领',
    semantic: 'info',
    icon: 'info',
    description: '执行人已认领任务',
  },
  IN_PROGRESS: {
    label: '处理中',
    semantic: 'info',
    icon: 'info',
    description: '任务正在执行',
  },
  COMPLETED: {
    label: '已完成',
    semantic: 'success',
    icon: 'check',
    description: '服务端已确认任务完成',
  },
  CANCELLED: {
    label: '已取消',
    semantic: 'critical',
    icon: 'critical',
    description: '任务已取消',
  },
  REJECTED: {
    label: '已驳回',
    semantic: 'critical',
    icon: 'critical',
    description: '任务结果被驳回，需跟进',
  },
  WAITING_REVIEW: {
    label: '待审核',
    semantic: 'warning',
    icon: 'warning',
    description: '等待审核处理',
  },
  WAITING_CORRECTION: {
    label: '待整改',
    semantic: 'warning',
    icon: 'warning',
    description: '等待整改回传',
  },
}

export function presentTaskStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
