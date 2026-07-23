import { get } from './http'

export type AdminClientDirectoryItem = {
  clientCode: string
  clientName: string
  status: 'ACTIVE' | 'DISABLED'
  brands: Array<{
    brandCode: string
    brandName: string
    status: 'ACTIVE' | 'DISABLED'
    sortOrder: number
  }>
  projectCount: number
}

export type AdminProjectDirectoryItem = {
  id: string
  projectCode: string
  projectName: string
  clientCode: string
  clientName: string | null
  startsOn: string
  endsOn: string | null
  regionNames: string[]
  networkCount: number
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
  publishedConfigurationCount: number | null
  draftConfigurationCount: number | null
  configurationStatus: 'NOT_CONFIGURED' | 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED_CHANGES' | 'NO_PERMISSION'
  dataComplete: boolean
  dataProblem: string | null
}

export type AdminClientProjectDirectoryView = {
  clients: AdminClientDirectoryItem[]
  projects: AdminProjectDirectoryItem[]
  allowedActions: Array<'CREATE_CLIENT' | 'CREATE_BRAND'>
  asOf: string
}

export function loadAdminClientProjectDirectory() {
  return get<AdminClientProjectDirectoryView>('/admin/client-project-directory').then((result) => result.data)
}
