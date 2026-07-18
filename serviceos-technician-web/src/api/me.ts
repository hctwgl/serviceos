import { apiGet, apiGetWithMeta } from './client'

export type MePersona = {
  id: string
  personaType: string
  status: string
  validFrom: string
  validTo: string | null
  version: number
}

export type MeProfile = {
  principalId: string
  tenantId: string
  displayName: string
  personas: MePersona[]
  contextVersion: string
  asOf: string
}

export type MeContext = {
  contextId: string
  portal: 'ADMIN' | 'NETWORK' | 'TECHNICIAN'
  personaType: string
  scopeType: string
  scopeRef: string
  scopeSummary: {
    organizationIds: string[]
    networkIds: string[]
    projectIds: string[]
  }
  version: string
}

export type MeContexts = {
  contexts: MeContext[]
  contextVersion: string
  asOf: string
}

export type MeNavigationItem = {
  pageId: string
  routeKey: string
  title: string
  order: number
  section: string
  requiredCapabilities: string[]
}

export type MeNavigation = {
  contextId: string
  portal: 'ADMIN' | 'NETWORK' | 'TECHNICIAN'
  contextVersion: string
  navigationCatalogVersion: string
  items: MeNavigationItem[]
  asOf: string
}

export type MeCapabilities = {
  contextId: string
  portal: 'ADMIN' | 'NETWORK' | 'TECHNICIAN'
  capabilityCodes: string[]
  contextVersion: string
  asOf: string
}

export function getMe() {
  return apiGet<MeProfile>('/me')
}

export function listMeContexts() {
  return apiGetWithMeta<MeContexts>('/me/contexts')
}

export function listMeNavigation(contextId: string, expectedContextVersion?: string) {
  const query: Record<string, string | undefined> = { contextId }
  if (expectedContextVersion) {
    query.expectedContextVersion = expectedContextVersion
  }
  return apiGetWithMeta<MeNavigation>('/me/navigation', query)
}

/** M188/M219：当前上下文有效 Capability 集合（导航辅助；业务 API 仍须重鉴权）。 */
export function listMeCapabilities(contextId: string, expectedContextVersion?: string) {
  const query: Record<string, string | undefined> = { contextId }
  if (expectedContextVersion) {
    query.expectedContextVersion = expectedContextVersion
  }
  return apiGetWithMeta<MeCapabilities>('/me/capabilities', query)
}
