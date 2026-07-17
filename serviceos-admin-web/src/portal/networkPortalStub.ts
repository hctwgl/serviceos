/**
 * Network Portal 最小接入 stub：独立消费 `/me/contexts` + `/me/navigation`。
 * 不实现完整 Network Portal UI；仅证明前端不能自报 networkId 扩权。
 */
import { listMeContexts, listMeNavigation, type MeNavigation } from '../api/me'

export async function loadNetworkPortalNavigation(forcedContextId?: string): Promise<{
  ok: boolean
  navigation: MeNavigation | null
  error: string | null
}> {
  try {
    const contexts = await listMeContexts()
    const networkContexts = contexts.data.contexts.filter((context) => context.portal === 'NETWORK')
    if (forcedContextId && !networkContexts.some((context) => context.contextId === forcedContextId)) {
      return { ok: false, navigation: null, error: '伪造 NETWORK 上下文被拒绝' }
    }
    const active = forcedContextId
      ? networkContexts.find((context) => context.contextId === forcedContextId)
      : networkContexts[0]
    if (!active) {
      return { ok: false, navigation: null, error: '无可用 NETWORK 上下文' }
    }
    const navigation = await listMeNavigation(active.contextId, contexts.data.contextVersion)
    return { ok: true, navigation: navigation.data, error: null }
  } catch {
    return { ok: false, navigation: null, error: '伪造 NETWORK 上下文被拒绝' }
  }
}
