import { apiGet } from './client'

export type ProjectClientOption = {
  clientId: string
  projectCount: number
}

export type ProjectRegionOption = {
  regionCode: string
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
