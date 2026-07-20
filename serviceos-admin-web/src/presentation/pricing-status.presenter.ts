import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

/**
 * 定价/试算状态。SHADOW 必须使用 shadow 语义，禁止成功色。
 */
const MAP: Record<string, SemanticStatusPresentation> = {
  SHADOW: {
    label: '影子/非正式',
    semantic: 'shadow',
    icon: 'shadow',
    description: '非正式试算结果，不可用于结算落账',
  },
  FORMAL: {
    label: '正式',
    semantic: 'success',
    icon: 'check',
    description: '正式计价结果',
  },
  DRAFT: {
    label: '草稿',
    semantic: 'warning',
    icon: 'info',
    description: '试算草稿，尚未确认',
  },
  UNCALCULABLE: {
    label: '不可计算',
    semantic: 'critical',
    icon: 'critical',
    description: '缺少计算所需事实或规则',
  },
  READY: {
    label: '可试算',
    semantic: 'info',
    icon: 'info',
    description: '具备试算条件',
  },
}

export function presentPricingStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) {
    // 模式字段常见值为 SHADOW / mode 字符串
    if (normalized.includes('SHADOW')) {
      return { ...MAP.SHADOW, rawCode: normalized }
    }
    return presentUnknownStatus(code)
  }
  return { ...hit, rawCode: normalized }
}
