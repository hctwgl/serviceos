import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  RUNNING: {
    label: '计时中',
    semantic: 'info',
    icon: 'clock',
    description: 'SLA 正在计时',
  },
  BREACHED: {
    label: '已超时',
    semantic: 'critical',
    icon: 'critical',
    description: '已超过截止时间，需立即处理',
  },
  MET: {
    label: '已达标',
    semantic: 'success',
    icon: 'check',
    description: '服务端确认在时限内达标',
  },
  MET_LATE: {
    label: '逾期达标',
    semantic: 'warning',
    icon: 'warning',
    description: '已完成但超过原定时限',
  },
  PAUSED: {
    label: '已暂停',
    semantic: 'neutral',
    icon: 'info',
    description: 'SLA 计时暂停',
  },
  OPEN: {
    label: '待处理',
    semantic: 'warning',
    icon: 'clock',
    description: '存在未关闭的时效实例',
  },
}

export function presentSlaStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}

/** 投影新鲜度：正式界面不直接展示 FRESH/LAGGING 等英文码。 */
export function presentFreshnessStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') {
    return {
      label: '暂时无法确认数据更新时间',
      semantic: 'stale',
      icon: 'sync',
      description: '请刷新后重试',
      rawCode: code ?? undefined,
    }
  }
  const normalized = code.trim().toUpperCase()
  switch (normalized) {
    case 'FRESH':
      return {
        label: '数据最新',
        semantic: 'success',
        icon: 'check',
        description: '服务端投影为最新',
        rawCode: normalized,
      }
    case 'LAGGING':
      return {
        label: '数据可能存在短暂延迟',
        semantic: 'stale',
        icon: 'sync',
        description: '可刷新获取最新数据',
        rawCode: normalized,
      }
    case 'REBUILDING':
      return {
        label: '数据正在重建',
        semantic: 'warning',
        icon: 'sync',
        description: '重建完成前结果可能不完整',
        rawCode: normalized,
      }
    case 'UNKNOWN':
      return {
        label: '暂时无法确认数据更新时间',
        semantic: 'stale',
        icon: 'sync',
        description: '请稍后刷新',
        rawCode: normalized,
      }
    default:
      return presentUnknownStatus(code)
  }
}
