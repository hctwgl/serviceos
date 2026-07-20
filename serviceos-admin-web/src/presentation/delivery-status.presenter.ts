import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  PREPARING: {
    label: '准备中',
    semantic: 'info',
    icon: 'sync',
    description: '外发准备中',
  },
  PENDING: {
    label: '待投递',
    semantic: 'warning',
    icon: 'clock',
    description: '等待投递',
  },
  RETRYING: {
    label: '重试中',
    semantic: 'warning',
    icon: 'sync',
    description: '投递失败后重试中',
  },
  RETRY_WAIT: {
    label: '等待重试',
    semantic: 'warning',
    icon: 'clock',
    description: '等待下一次重试',
  },
  DELIVERED: {
    label: '已送达',
    semantic: 'success',
    icon: 'check',
    description: '服务端确认已送达',
  },
  SUCCEEDED: {
    label: '成功',
    semantic: 'success',
    icon: 'check',
    description: '投递成功',
  },
  FAILED: {
    label: '失败',
    semantic: 'critical',
    icon: 'critical',
    description: '投递失败，需人工介入或重发',
  },
  MANUAL_INTERVENTION: {
    label: '需人工介入',
    semantic: 'critical',
    icon: 'critical',
    description: '自动重试已用尽',
  },
  ACKNOWLEDGED: {
    label: '已确认',
    semantic: 'success',
    icon: 'check',
    description: '对端已确认',
  },
}

export function presentDeliveryStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
