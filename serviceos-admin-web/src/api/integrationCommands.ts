import { apiPost, newIdempotencyKey } from './client'
import type { OutboundDelivery } from './outbound'

export function createBydReviewSubmission(sourceReviewCaseId: string) {
  return apiPost<OutboundDelivery>('/internal/integration/byd/review-submissions', {
    idempotencyKey: newIdempotencyKey('byd-submit'),
    body: { sourceReviewCaseId },
  })
}
