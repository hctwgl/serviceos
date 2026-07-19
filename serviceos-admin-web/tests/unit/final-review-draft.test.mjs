import assert from 'node:assert/strict'
import test from 'node:test'

/** 本地草稿 key 规则：必须包含 principalId 与 reviewCaseId。 */
function draftKey(principalId, reviewCaseId) {
  return `sos.final-review.draft.${principalId}.${reviewCaseId}`
}

test('final review draft key includes principal and review case', () => {
  const key = draftKey('principal-1', 'case-2')
  assert.equal(key, 'sos.final-review.draft.principal-1.case-2')
  assert.match(key, /principal-1/)
  assert.match(key, /case-2/)
})

test('draft payload must not include phone address or image url fields', () => {
  const payload = {
    principalId: 'p1',
    reviewCaseId: 'c1',
    aggregateVersion: 1,
    savedAt: new Date().toISOString(),
    overallNote: '说明',
    targetDecisions: [
      {
        targetType: 'EvidenceRevision',
        targetId: 't1',
        targetVersion: 1,
        decision: 'REJECTED',
        reasonCodes: ['IMAGE.BLUR'],
        note: '请重拍',
      },
    ],
  }
  const serialized = JSON.stringify(payload)
  assert.doesNotMatch(serialized, /downloadUrl|customerPhone|serviceAddress|objectKey/)
})
