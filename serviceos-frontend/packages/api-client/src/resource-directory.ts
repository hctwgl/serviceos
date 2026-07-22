import { get } from './http'

export type AdminServiceNetworkDirectoryItem = {
  id: string
  networkCode: string
  networkName: string
  partnerOrganizationName: string
  status: 'ACTIVE' | 'DEACTIVATED'
  regionCodes: string[]
  activeTechnicianCount: number
  updatedAt: string
}

export type AdminTechnicianDirectoryItem = {
  id: string
  displayName: string
  status: 'ACTIVE' | 'DISABLED'
  supportedClientKinds: Array<'TECHNICIAN_WEB' | 'TECHNICIAN_IOS'>
  networkNames: string[]
  approvedQualificationCodes: string[]
  pendingQualificationCount: number
  updatedAt: string
}

export type AdminResourceDirectoryPage = {
  networks: AdminServiceNetworkDirectoryItem[]
  technicians: AdminTechnicianDirectoryItem[]
  asOf: string
}

export function loadAdminResourceDirectory() {
  return get<AdminResourceDirectoryPage>('/admin/resource-directory').then((result) => result.data)
}
