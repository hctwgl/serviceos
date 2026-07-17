import {
  applyUiPreferencesToDocument,
  getUiPreferences,
  type UiPreferencesDocument,
} from '../api/uiPreferences'

let cached: UiPreferencesDocument | null = null
let loading: Promise<UiPreferencesDocument | null> | null = null

export function currentUiPreferences(): UiPreferencesDocument | null {
  return cached
}

/** 加载并应用当前主体 Admin UI 偏好；失败时不阻断页面。 */
export async function loadAndApplyUiPreferences(): Promise<UiPreferencesDocument | null> {
  if (loading) {
    return loading
  }
  loading = (async () => {
    try {
      const doc = await getUiPreferences()
      cached = doc
      applyUiPreferencesToDocument(doc)
      return doc
    } catch {
      cached = null
      return null
    } finally {
      loading = null
    }
  })()
  return loading
}

export function rememberUiPreferences(doc: UiPreferencesDocument) {
  cached = doc
  applyUiPreferencesToDocument(doc)
}
