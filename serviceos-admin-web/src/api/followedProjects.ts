import { apiDelete, apiGet, apiPut } from './client'

export type FollowedProjectItem = {
  projectId: string
  displayRef: string
  projectCode: string | null
  clientId: string | null
  status: string | null
  followedAt: string
  deepLink: string
  activeWorkOrderCount: number | null
  activeWorkOrderCountTruncated: boolean | null
  openReviewCount: number | null
  openReviewCountTruncated: boolean | null
  openCorrectionCount: number | null
  openCorrectionCountTruncated: boolean | null
  slaBreachedCount: number | null
  slaBreachedCountTruncated: boolean | null
  openTodoCount: number | null
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

/** 角标文案：缺权限时不展示该段；截断时后缀 +。 */
export function formatFollowedBadgeCount(
  count: number | null | undefined,
  truncated: boolean | null | undefined,
): string | null {
  if (count == null) {
    return null
  }
  return truncated ? `${count}+` : String(count)
}
