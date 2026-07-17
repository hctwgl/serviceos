/**
 * Technician Portal stub 兼容入口：委托真实导航加载，保持 PortalStubsPage 契约。
 */
import { loadTechnicianPortalNavigation as loadNav } from './technicianPortalNav'

export async function loadTechnicianPortalNavigation(forcedContextId?: string): Promise<{
  ok: boolean
  navigation: { items: { pageId: string }[] } | null
  error: string | null
}> {
  const state = await loadNav(forcedContextId)
  if (state.error && !state.activeContextId) {
    return { ok: false, navigation: null, error: state.error }
  }
  if (!state.activeContextId) {
    return { ok: false, navigation: null, error: state.error ?? '无可用 TECHNICIAN 上下文' }
  }
  return {
    ok: true,
    navigation: { items: state.items },
    error: null,
  }
}
