import { usableAccessToken } from '../../../auth/oidc'

export type LocalTargetDecision = {
  targetType: 'EvidenceRevision'
  targetId: string
  targetVersion: number
  decision: 'APPROVED' | 'REJECTED' | null
  reasonCodes: string[]
  note: string
}

export type FinalReviewLocalDraft = {
  principalId: string
  reviewCaseId: string
  aggregateVersion: number
  savedAt: string
  overallNote: string
  targetDecisions: LocalTargetDecision[]
}

const TTL_MS = 24 * 60 * 60 * 1000

/** 从 JWT payload 读取 sub，仅用于本地草稿分桶，不做验签。 */
function currentPrincipalId(): string {
  const token = usableAccessToken()
  if (!token) return 'anonymous'
  try {
    const payload = JSON.parse(atob(token.split('.')[1] ?? '')) as { sub?: string }
    return payload.sub || 'anonymous'
  } catch {
    return 'anonymous'
  }
}

function draftKey(principalId: string, reviewCaseId: string) {
  return `sos.final-review.draft.${principalId}.${reviewCaseId}`
}

export function loadFinalReviewDraft(
  reviewCaseId: string,
  aggregateVersion: number,
): FinalReviewLocalDraft | null {
  const principalId = currentPrincipalId()
  try {
    const raw = sessionStorage.getItem(draftKey(principalId, reviewCaseId))
    if (!raw) return null
    const draft = JSON.parse(raw) as FinalReviewLocalDraft
    if (draft.principalId !== principalId || draft.reviewCaseId !== reviewCaseId) {
      return null
    }
    if (Date.now() - Date.parse(draft.savedAt) > TTL_MS) {
      sessionStorage.removeItem(draftKey(principalId, reviewCaseId))
      return null
    }
    // 版本变化时不自动覆盖服务端数据；调用方提示“检测到旧版本草稿”。
    if (draft.aggregateVersion !== aggregateVersion) {
      return draft
    }
    return draft
  } catch {
    return null
  }
}

export function saveFinalReviewDraft(draft: Omit<FinalReviewLocalDraft, 'principalId' | 'savedAt'>) {
  const principalId = currentPrincipalId()
  const payload: FinalReviewLocalDraft = {
    ...draft,
    principalId,
    savedAt: new Date().toISOString(),
  }
  // 不保存客户电话、完整地址或图片 URL。
  sessionStorage.setItem(draftKey(principalId, draft.reviewCaseId), JSON.stringify(payload))
  return payload
}

export function clearFinalReviewDraftsForPrincipal() {
  const principalId = currentPrincipalId()
  const prefix = `sos.final-review.draft.${principalId}.`
  const keys: string[] = []
  for (let i = 0; i < sessionStorage.length; i += 1) {
    const key = sessionStorage.key(i)
    if (key && key.startsWith(prefix)) keys.push(key)
  }
  for (const key of keys) sessionStorage.removeItem(key)
}

export function clearAllFinalReviewDrafts() {
  const keys: string[] = []
  for (let i = 0; i < sessionStorage.length; i += 1) {
    const key = sessionStorage.key(i)
    if (key && key.startsWith('sos.final-review.draft.')) keys.push(key)
  }
  for (const key of keys) sessionStorage.removeItem(key)
}
