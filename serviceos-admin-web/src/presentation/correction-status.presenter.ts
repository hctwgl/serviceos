import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  OPEN: {
    label: '待处理',
    semantic: 'warning',
    icon: 'clock',
    description: '整改待启动',
  },
  IN_PROGRESS: {
    label: '整改中',
    semantic: 'info',
    icon: 'info',
    description: '整改正在进行',
  },
  RESUBMITTED: {
    label: '已重新提交',
    semantic: 'warning',
    icon: 'sync',
    description: '已回传，等待复审',
  },
  WAIVED: {
    label: '已豁免',
    semantic: 'neutral',
    icon: 'check',
    description: '整改已豁免关闭',
  },
  CLOSED: {
    label: '已关闭',
    semantic: 'success',
    icon: 'check',
    description: '整改已关闭',
  },
  CLOSED_OK: {
    label: '已关闭',
    semantic: 'success',
    icon: 'check',
    description: '整改已正常关闭',
  },
  CANCELLED: {
    label: '已取消',
    semantic: 'critical',
    icon: 'critical',
    description: '整改已取消',
  },
}

export function presentCorrectionStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
