import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  RECEIVED: {
    label: '已接收',
    semantic: 'warning',
    icon: 'clock',
    description: '工单已接入，等待进入履约',
  },
  ACTIVE: {
    label: '处理中',
    semantic: 'info',
    icon: 'info',
    description: '工单正在履约处理',
  },
  FULFILLED: {
    label: '已完成',
    semantic: 'success',
    icon: 'check',
    description: '服务端已确认履约完成',
  },
  CANCELLED: {
    label: '已取消',
    semantic: 'critical',
    icon: 'critical',
    description: '工单已取消，不可继续履约',
  },
  SUSPENDED: {
    label: '已挂起',
    semantic: 'neutral',
    icon: 'info',
    description: '工单暂时挂起',
  },
  CLOSED: {
    label: '已关闭',
    semantic: 'neutral',
    icon: 'check',
    description: '工单已关闭',
  },
}

export function presentWorkOrderStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
