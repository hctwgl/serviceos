/**
 * Network Portal 导航：独立消费 `/me/contexts` + `/me/navigation`。
 * 伪造 contextId 失败关闭，不能自报 networkId 扩权。
 */
import { listMeContexts, listMeNavigation, type MeContext, type MeNavigationItem } from '../api/me'
import { isConflictError } from '../api/client'
import { currentLocalOidcSession } from '../auth/oidc'

export type NetworkPortalNavState = {
  contexts: MeContext[]
  activeContextId: string | null
  contextVersion: string | null
  items: MeNavigationItem[]
  stale: boolean
  error: string | null
}

const EMPTY: NetworkPortalNavState = {
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
}

const STORAGE_KEY = 'serviceos.network.activeContextId'

export async function loadNetworkPortalNavigation(
  preferredContextId?: string | null,
): Promise<NetworkPortalNavState> {
  if (!currentLocalOidcSession().authenticated) {
    return { ...EMPTY }
  }
  try {
    const contextsResult = await listMeContexts()
    const networkContexts = contextsResult.data.contexts.filter(
      (context) => context.portal === 'NETWORK',
    )
    if (networkContexts.length === 0) {
      return {
        ...EMPTY,
        contexts: contextsResult.data.contexts,
        contextVersion: contextsResult.data.contextVersion,
        error: '当前主体没有可用的 NETWORK 上下文',
      }
    }
    if (
      preferredContextId &&
      !networkContexts.some((context) => context.contextId === preferredContextId)
    ) {
      return {
        ...EMPTY,
        contexts: contextsResult.data.contexts,
        contextVersion: contextsResult.data.contextVersion,
        error: '伪造 NETWORK 上下文被拒绝',
      }
    }
    const stored = preferredContextId ?? localStorage.getItem(STORAGE_KEY)
    const active =
      networkContexts.find((context) => context.contextId === stored) ?? networkContexts[0]
    localStorage.setItem(STORAGE_KEY, active.contextId)

    try {
      const navigation = await listMeNavigation(
        active.contextId,
        contextsResult.data.contextVersion,
      )
      return {
        contexts: contextsResult.data.contexts,
        activeContextId: active.contextId,
        contextVersion: navigation.data.contextVersion,
        items: navigation.data.items,
        stale: false,
        error: null,
      }
    } catch (error) {
      if (isConflictError(error)) {
        return {
          ...EMPTY,
          contexts: contextsResult.data.contexts,
          contextVersion: contextsResult.data.contextVersion,
          stale: true,
          error: '上下文版本已失效',
        }
      }
      throw error
    }
  } catch {
    return {
      ...EMPTY,
      error: 'Network Portal 导航加载失败',
    }
  }
}

export function networkPortalRoutePath(pageId: string): string {
  switch (pageId) {
    case 'NETWORK.WORKBENCH':
      return '/network-portal/workbench'
    case 'NETWORK.WORKORDER.LIST':
      return '/network-portal/work-orders'
    case 'NETWORK.TASK.QUEUE':
      return '/network-portal/tasks'
    case 'NETWORK.TECHNICIAN.LIST':
      return '/network-portal/technicians'
    case 'NETWORK.TECHNICIAN.ASSIGN':
      return '/network-portal/tasks'
    default:
      return '/network-portal/workbench'
  }
}
