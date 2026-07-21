import { apiGet } from './client'

export type ProjectClientOption = {
  clientId: string
  displayName: string
  projectCount: number
}

export type ProjectRegionOption = {
  regionCode: string
  regionName: string
  projectCount: number
}

export type ProjectReferenceOptions = {
  clients: ProjectClientOption[]
  regions: ProjectRegionOption[]
  asOf: string
}

export function getProjectReferenceOptions() {
  return apiGet<ProjectReferenceOptions>('/projects/reference-options')
}
