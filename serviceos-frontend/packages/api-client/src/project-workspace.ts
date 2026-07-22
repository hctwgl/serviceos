import { get } from './http'

export type AdminProjectFulfillmentProfile = {
  profileId: string
  profileName: string
  serviceProductName: string | null
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'RETIRED'
  stageCount: number | null
  formCount: number | null
  evidenceCount: number | null
  activeVersion: string | null
  effectiveFrom: string | null
  workflowSummary: string | null
  slaSummary: string | null
  updatedAt: string
  dataComplete: boolean
  dataProblem: string | null
}

export type AdminProjectWorkspaceView = {
  projectId: string
  projectCode: string
  projectName: string
  clientName: string | null
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
  startsOn: string
  endsOn: string | null
  regionNames: string[]
  networkNames: string[]
  configurationReadable: boolean
  fulfillmentProfiles: AdminProjectFulfillmentProfile[]
  activeWorkOrderCount: number | null
  activeWorkOrderCountTruncated: boolean | null
  dataComplete: boolean
  dataProblem: string | null
  asOf: string
}

export function loadAdminProjectWorkspace(projectId: string) {
  return get<AdminProjectWorkspaceView>(`/admin/projects/${projectId}/workspace`).then((result) => result.data)
}
