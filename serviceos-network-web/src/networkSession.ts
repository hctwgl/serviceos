import type { WebApiClient } from '@serviceos/web-core'

export type MeContext = { contextId: string; portal: string; scopeRef: string; version: string }
export type NavigationItem = { pageId: string; routeKey: string; title: string; order: number; section: string; requiredCapabilities: string[] }
export type NetworkSession = { contexts: MeContext[]; activeContextId: string; contextVersion: string; capabilities: string[]; navigation: NavigationItem[] }

type ContextsResponse = { contexts: MeContext[]; contextVersion: string }
type CapabilitiesResponse = { portal: string; contextVersion: string; capabilityCodes: string[] }
type NavigationResponse = { portal: string; contextVersion: string; items: NavigationItem[] }

/** Context 必须来自服务端本轮列表；Capability/导航只用于呈现，业务 API 仍独立鉴权。 */
export async function loadNetworkSession(api: WebApiClient, preferredContextId?: string): Promise<NetworkSession> {
  const contextsResult = await api.request<ContextsResponse>({ path: '/me/contexts' })
  const contexts = contextsResult.data.contexts.filter((item) => item.portal === 'NETWORK')
  if (contexts.length === 0) throw new Error('当前主体没有可用的 NETWORK 上下文')
  if (preferredContextId && !contexts.some((item) => item.contextId === preferredContextId)) throw new Error('NETWORK 上下文不属于当前主体')
  const activeContextId = preferredContextId ?? contexts[0].contextId
  const query = new URLSearchParams({ contextId: activeContextId, expectedContextVersion: contextsResult.data.contextVersion })
  const [capabilities, navigation] = await Promise.all([
    api.request<CapabilitiesResponse>({ path: `/me/capabilities?${query}` }),
    api.request<NavigationResponse>({ path: `/me/navigation?${query}` }),
  ])
  if (capabilities.data.portal !== 'NETWORK' || navigation.data.portal !== 'NETWORK') throw new Error('服务端返回了错误 Portal 的上下文数据')
  return Object.freeze({ contexts, activeContextId, contextVersion: navigation.data.contextVersion,
    capabilities: [...capabilities.data.capabilityCodes], navigation: [...navigation.data.items].sort((a, b) => a.order - b.order) })
}
