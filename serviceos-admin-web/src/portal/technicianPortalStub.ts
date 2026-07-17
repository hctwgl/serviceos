/**
 * Technician Portal 最小接入 stub：独立消费 `/me/contexts` + `/me/navigation`。
 * 不实现完整师傅 App；CONSUMER Persona 不会出现在 contexts 中。
 */
import { listMeContexts, listMeNavigation, type MeNavigation } from '../api/me'

export async function loadTechnicianPortalNavigation(forcedContextId?: string): Promise<{
  ok: boolean
  navigation: MeNavigation | null
  error: string | null
}> {
  try {
    const contexts = await listMeContexts()
    const technicianContexts = contexts.data.contexts.filter(
      (context) => context.portal === 'TECHNICIAN',
    )
    if (
      forcedContextId &&
      !technicianContexts.some((context) => context.contextId === forcedContextId)
    ) {
      return { ok: false, navigation: null, error: '伪造 TECHNICIAN 上下文被拒绝' }
    }
    const active = forcedContextId
      ? technicianContexts.find((context) => context.contextId === forcedContextId)
      : technicianContexts[0]
    if (!active) {
      return { ok: false, navigation: null, error: '无可用 TECHNICIAN 上下文' }
    }
    const navigation = await listMeNavigation(active.contextId, contexts.data.contextVersion)
    return { ok: true, navigation: navigation.data, error: null }
  } catch {
    return { ok: false, navigation: null, error: '伪造 TECHNICIAN 上下文被拒绝' }
  }
}
