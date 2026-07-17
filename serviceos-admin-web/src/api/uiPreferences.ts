import { apiDelete, apiGet, apiPut } from './client'

export type UiPreferenceKey =
  | 'theme'
  | 'density'
  | 'locale'
  | 'reduceMotion'
  | 'defaultSavedViews'
  | 'columnWidths'

export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM'
export type DensityPreference = 'COMFORTABLE' | 'COMPACT'

export type UiPreferenceEntry = {
  key: UiPreferenceKey
  value: unknown
  schemaVersion: number
  aggregateVersion: number
  updatedAt: string
}

export type UiPreferencesDocument = {
  portal: 'ADMIN'
  preferences: Partial<Record<UiPreferenceKey, UiPreferenceEntry>>
  asOf: string
}

export function getUiPreferences() {
  return apiGet<UiPreferencesDocument>('/me/ui-preferences', { portal: 'ADMIN' })
}

export function putUiPreferences(
  preferences: Partial<
    Record<
      UiPreferenceKey,
      { value: unknown; schemaVersion: number; expectedVersion?: number }
    >
  >,
) {
  return apiPut<UiPreferencesDocument>('/me/ui-preferences', {
    body: { portal: 'ADMIN', preferences },
  })
}

export function deleteUiPreference(key: UiPreferenceKey) {
  // portal 默认 ADMIN（服务端 default）；本切片不传其他 portal。
  return apiDelete(`/me/ui-preferences/${key}`)
}

/** 将偏好应用到 documentElement CSS 类与 data 属性。 */
export function applyUiPreferencesToDocument(doc: UiPreferencesDocument) {
  const root = document.documentElement
  const theme = (doc.preferences.theme?.value as ThemePreference | undefined) ?? 'SYSTEM'
  const density = (doc.preferences.density?.value as DensityPreference | undefined) ?? 'COMFORTABLE'
  const reduceMotion = Boolean(doc.preferences.reduceMotion?.value)

  root.dataset.theme = theme === 'SYSTEM' ? preferredSystemTheme() : theme.toLowerCase()
  root.dataset.density = density.toLowerCase()
  root.dataset.reduceMotion = reduceMotion ? 'true' : 'false'

  root.classList.toggle('theme-dark', root.dataset.theme === 'dark')
  root.classList.toggle('theme-light', root.dataset.theme === 'light')
  root.classList.toggle('density-compact', density === 'COMPACT')
  root.classList.toggle('density-comfortable', density === 'COMFORTABLE')
  root.classList.toggle('reduce-motion', reduceMotion)
}

function preferredSystemTheme(): 'dark' | 'light' {
  if (typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

export function defaultSavedViewIdForPage(
  doc: UiPreferencesDocument | null,
  pageId: string,
): string | null {
  const map = doc?.preferences.defaultSavedViews?.value
  if (map == null || typeof map !== 'object') {
    return null
  }
  const value = (map as Record<string, string | null>)[pageId]
  return typeof value === 'string' && value.length > 0 ? value : null
}
