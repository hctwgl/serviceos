/**
 * 业务枚举 → 中文展示。无映射时使用安全中文兜底，正式正文不直接显示英文枚举。
 */

const CLIENT_LABELS: Record<string, string> = {
  GEELY: '吉利汽车',
  BYD: '比亚迪',
  NIO: '蔚来',
  XPENG: '小鹏',
  LI: '理想',
  TESLA: '特斯拉',
  OTHER: '其他车企',
}

const SERVICE_LABELS: Record<string, string> = {
  INSTALLATION: '安装服务',
  SURVEY: '勘测服务',
  REPAIR: '维修服务',
  CORRECTION: '整改服务',
  SECOND_VISIT: '二次上门',
  HOME_CHARGING_SURVEY_INSTALL: '家充勘测安装',
  PILOT_SURVEY: '试点勘测',
  PILOT_INSTALL: '试点安装',
  PILOT_COMPLETION: '试点完工',
}

const ORIGIN_LABELS: Record<string, string> = {
  INTERNAL: '平台审核',
  CLIENT: '车企审核',
  EXTERNAL: '外部审核',
}

export function labelClientCode(code: string | null | undefined): string {
  if (code == null || code === '') return '未提供'
  const normalized = code.trim().toUpperCase()
  return CLIENT_LABELS[normalized] ?? '未知车企'
}

export function labelServiceProduct(code: string | null | undefined): string {
  if (code == null || code === '') return '未提供'
  const normalized = code.trim().toUpperCase()
  return SERVICE_LABELS[normalized] ?? '未知服务类型'
}

export function labelReviewOrigin(code: string | null | undefined): string {
  if (code == null || code === '') return '未提供'
  const normalized = code.trim().toUpperCase()
  return ORIGIN_LABELS[normalized] ?? '未知来源'
}

/** 通用安全兜底：正式 UI 用中文，原始值仅供诊断。 */
export function labelEnumOrFallback(
  code: string | null | undefined,
  fallbackLabel = '未识别项',
): { label: string; rawCode?: string } {
  if (code == null || code === '') {
    return { label: '未提供' }
  }
  const fromClient = CLIENT_LABELS[code.trim().toUpperCase()]
  if (fromClient) return { label: fromClient, rawCode: code }
  const fromService = SERVICE_LABELS[code.trim().toUpperCase()]
  if (fromService) return { label: fromService, rawCode: code }
  const fromOrigin = ORIGIN_LABELS[code.trim().toUpperCase()]
  if (fromOrigin) return { label: fromOrigin, rawCode: code }
  return { label: fallbackLabel, rawCode: code }
}
