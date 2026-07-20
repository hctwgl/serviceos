<script setup lang="ts">
/**
 * 通用状态徽章：必须指定领域 domain，禁止跨领域猜色。
 * 新代码优先直接使用 SemanticStatusTag + 对应 presenter。
 */
import { computed } from 'vue'
import type { SemanticStatusPresentation } from '../presentation/semantic-status'
import { presentWorkOrderStatus } from '../presentation/work-order-status.presenter'
import { presentTaskStatus } from '../presentation/task-status.presenter'
import { presentReviewStatus } from '../presentation/review-status.presenter'
import { presentCorrectionStatus } from '../presentation/correction-status.presenter'
import { presentEvidenceStatus } from '../presentation/evidence-status.presenter'
import { presentSlaStatus } from '../presentation/sla-status.presenter'
import { presentDeliveryStatus } from '../presentation/delivery-status.presenter'
import { presentPricingStatus } from '../presentation/pricing-status.presenter'
import { presentUnknownStatus } from '../presentation/semantic-status'
import { statusLabel } from '../product/statusLabels'
import SemanticStatusTag from './business/SemanticStatusTag.vue'

export type StatusDomain =
  | 'work-order'
  | 'task'
  | 'review'
  | 'correction'
  | 'evidence'
  | 'sla'
  | 'delivery'
  | 'pricing'
  | 'unknown'

const props = withDefaults(
  defineProps<{
    status: string | null | undefined
    /** 领域，缺省 unknown（仅中性兜底，不猜业务色） */
    domain?: StatusDomain
    presentation?: SemanticStatusPresentation
  }>(),
  {
    domain: 'unknown',
    presentation: undefined,
  },
)

const resolved = computed((): SemanticStatusPresentation => {
  if (props.presentation) return props.presentation
  switch (props.domain) {
    case 'work-order':
      return presentWorkOrderStatus(props.status)
    case 'task':
      return presentTaskStatus(props.status)
    case 'review':
      return presentReviewStatus(props.status)
    case 'correction':
      return presentCorrectionStatus(props.status)
    case 'evidence':
      return presentEvidenceStatus(props.status)
    case 'sla':
      return presentSlaStatus(props.status)
    case 'delivery':
      return presentDeliveryStatus(props.status)
    case 'pricing':
      return presentPricingStatus(props.status)
    default: {
      // 迁移期：未声明领域时只提供中文标签 + 中性语义，禁止跨领域猜色。
      const unknown = presentUnknownStatus(props.status)
      if (props.status == null || props.status === '') return unknown
      return {
        label: statusLabel(props.status),
        semantic: 'neutral' as const,
        icon: 'info' as const,
        description: '未指定状态领域，仅显示中性标签',
        rawCode: props.status,
      }
    }
  }
})
</script>

<template>
  <SemanticStatusTag :presentation="resolved" :show-raw-in-dev="true" />
</template>
