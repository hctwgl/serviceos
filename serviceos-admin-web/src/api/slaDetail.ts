import { apiGet } from './client'
import type { SlaInstanceItem } from './sla'

export type SlaInstanceDetail = {
  instance: SlaInstanceItem
  segments: Array<Record<string, unknown>>
  milestones: Array<Record<string, unknown>>
  asOf: string
}

export function getSlaInstance(slaInstanceId: string) {
  return apiGet<SlaInstanceDetail>(`/sla-instances/${slaInstanceId}`)
}
