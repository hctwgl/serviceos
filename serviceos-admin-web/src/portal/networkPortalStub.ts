/**
 * Admin 仅保留 M188 诊断入口：验证 NETWORK Context 与服务端导航失败关闭。
 * 正式产品 UI 已迁移到独立 `serviceos-network-web`，不得在 Admin 恢复业务路由。
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
