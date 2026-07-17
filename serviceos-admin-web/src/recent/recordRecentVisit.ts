import { touchRecentResource, type RecentResourceType } from '../api/recentResources'
import { currentLocalOidcSession } from '../auth/oidc'

/**
 * 详情页成功打开后记录最近访问。失败静默（不影响主流程）；不重试风暴。
 */
export function recordRecentVisit(input: {
  resourceType: RecentResourceType
  resourceId: string
  pageId?: string
  displayRef?: string
}): void {
  if (!input.resourceId || !currentLocalOidcSession().authenticated) {
    return
  }
  void touchRecentResource({
    resourceType: input.resourceType,
    resourceId: input.resourceId,
    pageId: input.pageId,
    displayRef: input.displayRef,
  }).catch(() => {
    /* 最近访问为体验增强；写失败不阻断详情 */
  })
}
