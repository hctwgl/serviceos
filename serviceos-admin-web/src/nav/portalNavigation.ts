import {
  listMeContexts,
  listMeNavigation,
  type MeContext,
  type MeNavigationItem,
} from '../api/me'
import { isConflictError } from '../api/client'
import { currentLocalOidcSession } from '../auth/oidc'

export type PortalNavState = {
  contexts: MeContext[]
  activeContextId: string | null
  contextVersion: string | null
  items: MeNavigationItem[]
  stale: boolean
  error: string | null
}

const EMPTY: PortalNavState = {
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
}

const STORAGE_KEY = 'serviceos.admin.activeContextId'

/**
 * M188：Admin 导航只消费服务端 `/me/contexts` + `/me/navigation`。
 * 前端切换 contextId 不能扩权；旧 contextVersion 失败关闭后强制重载。
 */
export async function loadAdminPortalNavigation(
  preferredContextId?: string | null,
): Promise<PortalNavState> {
  if (!currentLocalOidcSession().authenticated) {
    return { ...EMPTY }
  }
  try {
    const contextsResult = await listMeContexts()
    const adminContexts = contextsResult.data.contexts.filter((context) => context.portal === 'ADMIN')
    if (adminContexts.length === 0) {
      return {
        ...EMPTY,
        contexts: contextsResult.data.contexts,
        contextVersion: contextsResult.data.contextVersion,
        error: '当前主体没有可用的 ADMIN 上下文',
      }
    }
    const stored = preferredContextId ?? localStorage.getItem(STORAGE_KEY)
    const active =
      adminContexts.find((context) => context.contextId === stored) ?? adminContexts[0]
    localStorage.setItem(STORAGE_KEY, active.contextId)

    try {
      const navigation = await listMeNavigation(active.contextId, contextsResult.data.contextVersion)
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
        const refreshed = await listMeContexts()
        const retryContext =
          refreshed.data.contexts.find((context) => context.contextId === active.contextId) ??
          refreshed.data.contexts.find((context) => context.portal === 'ADMIN')
        if (!retryContext) {
          return {
            ...EMPTY,
            contexts: refreshed.data.contexts,
            contextVersion: refreshed.data.contextVersion,
            stale: true,
            error: '上下文版本已失效',
          }
        }
        const navigation = await listMeNavigation(
          retryContext.contextId,
          refreshed.data.contextVersion,
        )
        return {
          contexts: refreshed.data.contexts,
          activeContextId: retryContext.contextId,
          contextVersion: navigation.data.contextVersion,
          items: navigation.data.items,
          stale: true,
          error: null,
        }
      }
      throw error
    }
  } catch (error) {
    return {
      ...EMPTY,
      error: error instanceof Error ? error.message : '无法加载 Portal 导航',
    }
  }
}

export function navItemVisible(items: MeNavigationItem[], pageId: string): boolean {
  return items.some((item) => item.pageId === pageId)
}

export function routePathFor(item: MeNavigationItem): string {
  return `/${item.routeKey.replace(/^\//, '')}`
}
