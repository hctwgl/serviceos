import { apiDelete, apiGet, apiPut } from './client'

export type FollowedProjectItem = {
  projectId: string
  displayRef: string
  projectCode: string | null
  clientId: string | null
  status: string | null
  followedAt: string
  deepLink: string
}

export type FollowedProjectPage = {
  items: FollowedProjectItem[]
  asOf: string
}

export function listFollowedProjects(limit = 20) {
  return apiGet<FollowedProjectPage>('/me/followed-projects', {
    portal: 'ADMIN',
    limit: String(limit),
  })
}

export function getFollowedProjectStatus(projectId: string) {
  return apiGet<{ projectId: string; followed: boolean }>(
    `/me/followed-projects/${projectId}/status`,
    { portal: 'ADMIN' },
  )
}

export function followProject(body: { projectId: string; displayRef?: string }) {
  return apiPut<FollowedProjectItem>('/me/followed-projects', {
    body: {
      portal: 'ADMIN',
      projectId: body.projectId,
      displayRef: body.displayRef ?? null,
    },
  })
}

export function unfollowProject(projectId: string) {
  return apiDelete(`/me/followed-projects/${projectId}?portal=ADMIN`)
}
