import { apiDelete, apiGet, apiPost, apiPut, quotedVersion } from './client'

export type SavedViewPageId =
  | 'ADMIN.TASK.QUEUE'
  | 'ADMIN.WORKORDER.LIST'
  | 'ADMIN.CORRECTION.QUEUE'

export type SavedViewVisibility = 'PRIVATE' | 'ROLE' | 'TENANT'

export type SavedViewFilterClause = {
  field: string
  operator: 'EQ'
  value: string
}

export type SavedViewFilterAst = {
  clauses: SavedViewFilterClause[]
}

export type SavedView = {
  id: string
  ownerPrincipalId: string
  portal: 'ADMIN'
  pageId: SavedViewPageId
  name: string
  visibility: SavedViewVisibility
  sharedScopeRef: string | null
  schemaVersion: number
  filter: SavedViewFilterAst
  sort: { fields: { field: string; direction: 'ASC' | 'DESC' }[] } | null
  columns: string[] | null
  isDefault: boolean
  aggregateVersion: number
  createdAt: string
  updatedAt: string
}

export type SavedViewList = {
  items: SavedView[]
  asOf: string
}

export function listSavedViews(pageId: SavedViewPageId) {
  return apiGet<SavedViewList>('/me/saved-views', { pageId })
}

export function createSavedView(body: {
  pageId: SavedViewPageId
  name: string
  schemaVersion: number
  filter: SavedViewFilterAst
  columns?: string[]
  isDefault?: boolean
}) {
  return apiPost<SavedView>('/me/saved-views', { body })
}

export function updateSavedView(
  id: string,
  aggregateVersion: number,
  body: {
    name: string
    schemaVersion: number
    filter: SavedViewFilterAst
    columns?: string[]
    isDefault?: boolean
  },
) {
  return apiPut<SavedView>(`/me/saved-views/${id}`, {
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

export function deleteSavedView(id: string) {
  return apiDelete(`/me/saved-views/${id}`)
}

export function shareSavedView(
  id: string,
  aggregateVersion: number,
  body: { visibility: SavedViewVisibility; sharedScopeRef?: string | null },
) {
  return apiPost<SavedView>(`/saved-views/${id}:share`, {
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}

/** 将当前页面筛选值编码为受控 filter AST（仅非空字段）。 */
export function filtersToAst(filters: Record<string, string | undefined>): SavedViewFilterAst {
  const clauses: SavedViewFilterClause[] = []
  for (const [field, value] of Object.entries(filters)) {
    if (value != null && value !== '') {
      clauses.push({ field, operator: 'EQ', value })
    }
  }
  return { clauses }
}

/** 从 SavedView filter AST 还原页面筛选值。 */
export function astToFilters(filter: SavedViewFilterAst): Record<string, string> {
  const next: Record<string, string> = {}
  for (const clause of filter.clauses ?? []) {
    if (clause.operator === 'EQ' && clause.field) {
      next[clause.field] = clause.value
    }
  }
  return next
}

export function visibilityLabel(visibility: SavedViewVisibility): string {
  switch (visibility) {
    case 'PRIVATE':
      return '私有'
    case 'ROLE':
      return '角色共享'
    case 'TENANT':
      return '租户共享'
  }
}
