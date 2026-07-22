import { get, newIdempotencyKey, post } from './http'

export type ProjectPositionCode =
  | 'CUSTOMER_SERVICE_MANAGER'
  | 'PROJECT_ASSISTANT'
  | 'PROJECT_MANAGER'

export type ProjectTeamMember = {
  memberId: string
  principalId: string
  displayName: string | null
  employeeNumber: string | null
  status: 'ACTIVE' | 'ENDED'
  validFrom: string
  version: number
  dataComplete: boolean
}

export type ProjectTeamCandidate = {
  principalId: string
  displayName: string
  employeeNumber: string | null
  alreadyMember: boolean
}

export type ProjectTeamRegionOption = {
  code: string
  name: string
  level: 'CITY' | 'COUNTRY' | 'DISTRICT' | 'PROVINCE'
  parentCode: string | null
}

export type ProjectRegionPersonnelAssignment = {
  assignmentId: string
  regionCode: string
  regionName: string
  regionLevel: ProjectTeamRegionOption['level']
  position: ProjectPositionCode
  positionName: string
  memberId: string
  principalId: string
  displayName: string | null
  allowInheritance: boolean
  validFrom: string
  version: number
  changeReason: string
  dataComplete: boolean
}

export type ProjectRegionPersonnelMatch = {
  position: ProjectPositionCode
  positionName: string
  assignmentId: string
  principalId: string
  displayName: string | null
  matchedRegionCode: string
  matchedRegionName: string
  inherited: boolean
  dataComplete: boolean
}

export type ProjectRegionPersonnelMatchResult = {
  projectId: string
  requestedRegionCode: string
  requestedRegionName: string
  matches: ProjectRegionPersonnelMatch[]
  missingPositions: ProjectPositionCode[]
  matchedAt: string
}

export type ProjectTeamWorkspace = {
  projectId: string
  projectCode: string
  projectName: string
  projectStatus: 'ACTIVE' | 'CLOSED' | 'DRAFT' | 'SUSPENDED'
  members: ProjectTeamMember[]
  assignments: ProjectRegionPersonnelAssignment[]
  candidates: ProjectTeamCandidate[]
  regions: ProjectTeamRegionOption[]
  allowedActions: Array<'ADD_MEMBER' | 'ASSIGN_REGION_PERSONNEL'>
  asOf: string
}

export type AssignProjectRegionPersonnelInput = {
  regionCode: string
  positionCode: ProjectPositionCode
  principalId: string
  expectedCurrentAssignmentId: string | null
  allowInheritance: boolean
  reason: string
}

export function loadProjectTeamWorkspace(projectId: string) {
  return get<ProjectTeamWorkspace>(`/projects/${projectId}/team-regions`).then((result) => result.data)
}

export function matchProjectRegionPersonnel(projectId: string, regionCode: string) {
  return get<ProjectRegionPersonnelMatchResult>(`/projects/${projectId}/team-regions:match`, {
    regionCode,
  }).then((result) => result.data)
}

export function addProjectTeamMember(projectId: string, principalId: string) {
  return post<ProjectTeamMember>(
    `/projects/${projectId}/team-members`,
    { principalId },
    { 'Idempotency-Key': newIdempotencyKey('project-team-member') },
  ).then((result) => result.data)
}

export function assignProjectRegionPersonnel(
  projectId: string,
  input: AssignProjectRegionPersonnelInput,
) {
  return post<ProjectRegionPersonnelAssignment>(
    `/projects/${projectId}/region-personnel:assign`,
    input,
    { 'Idempotency-Key': newIdempotencyKey('project-region-personnel') },
  ).then((result) => result.data)
}
