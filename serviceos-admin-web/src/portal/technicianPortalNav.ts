/**
 * Technician Portal 导航：独立消费 `/me/contexts` + `/me/navigation`。
 * 伪造 contextId 失败关闭；API 请求携带 X-Technician-Context。
 */
import { listMeContexts, listMeNavigation, type MeContext, type MeNavigationItem } from '../api/me'
import { isConflictError } from '../api/client'
import { currentLocalOidcSession } from '../auth/oidc'

export type TechnicianPortalNavState = {
  contexts: MeContext[]
  activeContextId: string | null
  contextVersion: string | null
  items: MeNavigationItem[]
  stale: boolean
  error: string | null
}

const EMPTY: TechnicianPortalNavState = {
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
}

const STORAGE_KEY = 'serviceos.technician.activeContextId'

export async function loadTechnicianPortalNavigation(
  preferredContextId?: string | null,
): Promise<TechnicianPortalNavState> {
  if (!currentLocalOidcSession().authenticated) {
    return { ...EMPTY }
  }
  try {
    const contextsResult = await listMeContexts()
    const technicianContexts = contextsResult.data.contexts.filter(
      (context) => context.portal === 'TECHNICIAN',
    )
    if (technicianContexts.length === 0) {
      return {
        ...EMPTY,
        contexts: contextsResult.data.contexts,
        contextVersion: contextsResult.data.contextVersion,
        error: '当前主体没有可用的 TECHNICIAN 上下文',
      }
    }
    if (
      preferredContextId &&
      !technicianContexts.some((context) => context.contextId === preferredContextId)
    ) {
      return {
        ...EMPTY,
        contexts: contextsResult.data.contexts,
        contextVersion: contextsResult.data.contextVersion,
        error: '伪造 TECHNICIAN 上下文被拒绝',
      }
    }
    const stored = preferredContextId ?? localStorage.getItem(STORAGE_KEY)
    const active =
      technicianContexts.find((context) => context.contextId === stored) ?? technicianContexts[0]
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
      error: 'Technician Portal 导航加载失败',
    }
  }
}

export function technicianPortalRoutePath(pageId: string): string {
  switch (pageId) {
    case 'TECHNICIAN.TASK.LIST':
      return '/technician-portal/task-feed'
    case 'TECHNICIAN.SCHEDULE':
      return '/technician-portal/schedule'
    case 'TECHNICIAN.SYNC.SUMMARY':
      return '/technician-portal/sync-summary'
    case 'TECHNICIAN.ME':
      return '/technician-portal/me'
    default:
      return '/technician-portal/task-feed'
  }
}
