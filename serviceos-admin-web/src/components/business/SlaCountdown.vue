<script setup lang="ts">
/**
 * SLA 展示：消费 sla presenter；倒计时视觉可更新，
 * aria-live 仅在进入警告/超时等关键阶段播报（由父级传入 liveAnnouncement）。
 */
import { computed, ref, watch } from 'vue'
import { Tooltip } from 'ant-design-vue'
import { presentSlaStatus } from '../../presentation/sla-status.presenter'
import { presentDateTime } from '../../presentation/date-time.presenter'
import SemanticStatusTag from './SemanticStatusTag.vue'

const props = defineProps<{
  status?: string | null
  displayText?: string | null
  dueAt?: string | null
}>()

const presentation = computed(() => {
  const base = presentSlaStatus(props.status)
  if (props.displayText) {
    return { ...base, label: props.displayText }
  }
  return base
})

const duePresented = computed(() => presentDateTime(props.dueAt))

const liveMessage = ref('')
watch(
  () => props.status,
  (next, prev) => {
    if (!next || next === prev) return
    const normalized = next.toUpperCase()
    if (normalized === 'BREACHED') {
      liveMessage.value = '服务时效已超时，请立即处理'
    } else if (normalized === 'MET_LATE') {
      liveMessage.value = '服务时效逾期达标'
    } else if (normalized === 'RUNNING' && prev?.toUpperCase() === 'BREACHED') {
      liveMessage.value = '服务时效已恢复计时'
    } else {
      liveMessage.value = ''
    }
  },
)
</script>

<template>
  <span class="sla-countdown" data-testid="sla-countdown">
    <Tooltip
      v-if="duePresented"
      :title="`截止时间：${duePresented.tooltip}`"
    >
      <SemanticStatusTag :presentation="presentation" />
    </Tooltip>
    <SemanticStatusTag v-else :presentation="presentation" />
    <span v-if="duePresented" class="sla-countdown__due">
      截止 {{ duePresented.absolute }}
    </span>
    <span class="sr-only" aria-live="polite">{{ liveMessage }}</span>
  </span>
</template>

<style scoped>
.sla-countdown {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.sla-countdown__due {
  font-size: var(--sos-font-size-xs, 12px);
  color: var(--sos-color-text-tertiary, #7b8494);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
