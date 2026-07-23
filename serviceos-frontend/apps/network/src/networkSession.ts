import { apiGet } from './api/client'

export type MeContext = { contextId: string; portal: string; scopeRef: string; version: string }
export type NavigationItem = {
  pageId: string
  routeKey: string
  title: string
  order: number
  section: string
  requiredCapabilities: string[]
}
export type NetworkSession = {
  contexts: MeContext[]
  activeContextId: string
  contextVersion: string
  capabilities: string[]
  navigation: NavigationItem[]
}

type ContextsResponse = { contexts: MeContext[]; contextVersion: string }
type CapabilitiesResponse = { portal: string; contextVersion: string; capabilityCodes: string[] }
type NavigationResponse = { portal: string; contextVersion: string; items: NavigationItem[] }

/** Context 必须来自服务端本轮列表；Capability/导航只用于呈现，业务 API 仍独立鉴权。 */
export async function loadNetworkSession(preferredContextId?: string): Promise<NetworkSession> {
  const contextsResult = await apiGet<ContextsResponse>('/me/contexts')
  const contexts = contextsResult.contexts.filter((item) => item.portal === 'NETWORK')
  const first = contexts[0]
  if (!first) throw new Error('当前主体没有可用的 NETWORK 上下文')
  if (preferredContextId && !contexts.some((item) => item.contextId === preferredContextId)) {
    throw new Error('NETWORK 上下文不属于当前主体')
  }
  const activeContextId = preferredContextId ?? first.contextId
  const query = { contextId: activeContextId, expectedContextVersion: contextsResult.contextVersion }
  const [capabilities, navigation] = await Promise.all([
    apiGet<CapabilitiesResponse>('/me/capabilities', query),
    apiGet<NavigationResponse>('/me/navigation', query),
  ])
  if (capabilities.portal !== 'NETWORK' || navigation.portal !== 'NETWORK') {
    throw new Error('服务端返回了错误 Portal 的上下文数据')
  }
  return Object.freeze({
    contexts,
    activeContextId,
    contextVersion: navigation.contextVersion,
    capabilities: [...capabilities.capabilityCodes],
    navigation: [...navigation.items].sort((a, b) => a.order - b.order),
  })
}
