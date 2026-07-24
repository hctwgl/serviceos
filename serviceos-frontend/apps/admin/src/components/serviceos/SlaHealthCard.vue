<script setup lang="ts">
import { computed } from 'vue'
import { ClockCircleOutlined } from '@serviceos/design-system'

const props = withDefaults(defineProps<{
  riskCount: number | null | undefined
  breachedCount?: number | null
  totalCount?: number | null
  title?: string
  description?: string
}>(), {
  breachedCount: null,
  totalCount: null,
  title: 'SLA 健康度',
  description: '按当前可见工单投影计算风险状态',
})

const unavailable = computed(() => props.riskCount === null || props.riskCount === undefined)
const state = computed(() => {
  if (unavailable.value) return { label: '暂时无法获取', tone: 'neutral' }
  if ((props.breachedCount ?? 0) > 0) return { label: '存在超时', tone: 'danger' }
  if ((props.riskCount ?? 0) > 0) return { label: '需要关注', tone: 'warning' }
  return { label: '运行稳定', tone: 'good' }
})

const value = computed(() => {
  if (unavailable.value) return '—'
  if ((props.breachedCount ?? 0) > 0) return `${props.breachedCount} 项超时`
  if ((props.riskCount ?? 0) > 0) return `${props.riskCount} 项风险`
  return '无风险'
})
</script>

<template>
  <section class="sos-sla-health" :class="`sos-sla-health--${state.tone}`" aria-label="SLA 健康度">
    <div class="sos-sla-health__icon"><ClockCircleOutlined /></div>
    <div class="sos-sla-health__body">
      <span>{{ title }}</span>
      <strong>{{ value }}</strong>
      <small>{{ state.label }} · {{ description }}</small>
    </div>
    <div v-if="totalCount !== null && totalCount !== undefined" class="sos-sla-health__total">
      <b>{{ totalCount }}</b>
      <span>可见工单</span>
    </div>
  </section>
</template>
