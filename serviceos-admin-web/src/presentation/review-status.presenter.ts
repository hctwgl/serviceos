import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  OPEN: {
    label: '待处理',
    semantic: 'warning',
    icon: 'clock',
    description: '审核案例待裁决',
  },
  APPROVED: {
    label: '已通过',
    semantic: 'success',
    icon: 'check',
    description: '服务端已确认审核通过',
  },
  REJECTED: {
    label: '已驳回',
    semantic: 'critical',
    icon: 'critical',
    description: '审核驳回，需整改或跟进',
  },
  FORCE_APPROVED: {
    label: '强制通过',
    semantic: 'warning',
    icon: 'warning',
    description: '已强制通过，请核对审计记录',
  },
  REOPENED: {
    label: '已重开',
    semantic: 'warning',
    icon: 'warning',
    description: '审核案例已重开',
  },
  INTERNAL: {
    label: '平台审核',
    semantic: 'info',
    icon: 'info',
    description: '内部审核来源',
  },
  CLIENT: {
    label: '车企审核',
    semantic: 'info',
    icon: 'info',
    description: '车企侧审核来源',
  },
  EXTERNAL: {
    label: '外部审核',
    semantic: 'neutral',
    icon: 'info',
    description: '外部审核来源',
  },
}

export function presentReviewStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
