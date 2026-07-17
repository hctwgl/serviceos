import { apiGet, apiPut } from './client'

export type RecentResourceType =
  | 'WORK_ORDER'
  | 'TASK'
  | 'PROJECT'
  | 'NETWORK'
  | 'TECHNICIAN'

export type RecentResourceItem = {
  resourceType: RecentResourceType
  resourceId: string
  pageId: string | null
  displayRef: string
  lastVisitedAt: string
  deepLink: string
}

export type RecentResourcePage = {
  items: RecentResourceItem[]
  asOf: string
}

export function listRecentResources(limit = 20) {
  return apiGet<RecentResourcePage>('/me/recent-resources', {
    portal: 'ADMIN',
    limit: String(limit),
  })
}

export function touchRecentResource(body: {
  resourceType: RecentResourceType
  resourceId: string
  pageId?: string
  displayRef?: string
}) {
  return apiPut<RecentResourceItem>('/me/recent-resources', {
    body: {
      portal: 'ADMIN',
      resourceType: body.resourceType,
      resourceId: body.resourceId,
      pageId: body.pageId ?? null,
      displayRef: body.displayRef ?? null,
    },
  })
}
