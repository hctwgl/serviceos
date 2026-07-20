/**
 * 跨 Portal 语义状态模型（落实 product/06）。
 * 组件只消费 SemanticStatusPresentation，不得直接把领域枚举映射为颜色。
 */

export type SemanticStatus =
  | 'neutral'
  | 'info'
  | 'success'
  | 'warning'
  | 'critical'
  | 'stale'
  | 'offline'
  | 'shadow'

export type SemanticStatusPresentation = {
  /** 面向运营人员的中文标签 */
  label: string
  semantic: SemanticStatus
  /** 可选图标名（由展示组件解析为 Ant Icon），禁止仅靠颜色表达 */
  icon?:
    | 'check'
    | 'info'
    | 'warning'
    | 'critical'
    | 'clock'
    | 'offline'
    | 'shadow'
    | 'sync'
  /** 辅助说明：风险原因、下一步、刷新提示等 */
  description?: string
  /** 原始领域码，仅开发诊断可见 */
  rawCode?: string
}

/** Ant Design Tag color 映射：品牌色不得用于 success/critical。 */
export function antTagColorForSemantic(semantic: SemanticStatus): string {
  switch (semantic) {
    case 'success':
      return 'success'
    case 'warning':
    case 'stale':
      return 'warning'
    case 'critical':
      return 'error'
    case 'info':
      return 'processing'
    case 'shadow':
      return 'purple'
    case 'offline':
      return 'default'
    case 'neutral':
    default:
      return 'default'
  }
}

export function presentUnknownStatus(code: string | null | undefined): SemanticStatusPresentation {
  if (code == null || code === '') {
    return {
      label: '未提供',
      semantic: 'neutral',
      description: '服务端未返回状态',
    }
  }
  return {
    label: '状态未知',
    semantic: 'neutral',
    icon: 'info',
    description: '系统暂无法识别该状态，请刷新或联系管理员',
    rawCode: code,
  }
}
