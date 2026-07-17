/**
 * 兼容 M188 stub 入口：委托给 Network Portal 导航加载器。
 * 完整 UI 见 `/network-portal/*`（M194）。
 */
import {
  loadNetworkPortalNavigation as loadNetworkPortalNavState,
} from './networkPortalNav'

export async function loadNetworkPortalNavigation(forcedContextId?: string): Promise<{
  ok: boolean
  navigation: { items: { pageId: string }[] } | null
  error: string | null
}> {
  const state = await loadNetworkPortalNavState(forcedContextId)
  if (state.error || !state.activeContextId) {
    return {
      ok: false,
      navigation: null,
      error: state.error ?? '无可用 NETWORK 上下文',
    }
  }
  return {
    ok: true,
    navigation: { items: state.items },
    error: null,
  }
}
