import { apiGet } from './client'

export type SearchResourceType = 'WORK_ORDER' | 'EXTERNAL_ORDER' | 'NETWORK' | 'TECHNICIAN'

export type ControlledSearchHit = {
  resourceRef: string
  type: SearchResourceType
  primaryLabel: string
  maskedSecondaryLabel: string | null
  matchReason: string
  deepLink: string
}

export type ControlledSearchResult = {
  items: ControlledSearchHit[]
  meta: {
    qDigest: string
    requestedTypes: SearchResourceType[]
    searchedTypes: SearchResourceType[]
    omittedTypes: SearchResourceType[]
  }
  asOf: string
}

export function searchResources(q: string, types: SearchResourceType[]) {
  return apiGet<ControlledSearchResult>('/search', {
    q,
    types: types.length > 0 ? types.join(',') : undefined,
  })
}
