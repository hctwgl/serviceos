import { listMeCapabilities } from './me'

const CONTEXT_STORAGE_KEY = 'serviceos.admin.activeContextId'

/**
 * 软门禁：读取当前 Admin 上下文的 capability 集合。
 * 仅用于隐藏按钮；业务 API 仍由后端鉴权失败关闭。
 */
export async function loadActiveCapabilityCodes(): Promise<string[]> {
  const contextId = localStorage.getItem(CONTEXT_STORAGE_KEY)
  if (!contextId) {
    return []
  }
  try {
    const result = await listMeCapabilities(contextId)
    return result.data.capabilityCodes ?? []
  } catch {
    return []
  }
}

export function hasCapability(codes: string[], required: string): boolean {
  return codes.includes(required)
}
