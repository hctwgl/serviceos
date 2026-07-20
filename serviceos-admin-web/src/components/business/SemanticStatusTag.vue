<script setup lang="ts">
import { computed } from 'vue'
import { Tooltip } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  CloudOutlined,
  ExperimentOutlined,
  SyncOutlined,
} from '@ant-design/icons-vue'
import type { SemanticStatusPresentation } from '../../presentation/semantic-status'

const props = defineProps<{
  presentation: SemanticStatusPresentation
  /** 开发环境可在 Tooltip 中附带原始码 */
  showRawInDev?: boolean
}>()

const icon = computed(() => {
  switch (props.presentation.icon) {
    case 'check':
      return CheckCircleOutlined
    case 'warning':
      return WarningOutlined
    case 'critical':
      return CloseCircleOutlined
    case 'clock':
      return ClockCircleOutlined
    case 'offline':
      return CloudOutlined
    case 'shadow':
      return ExperimentOutlined
    case 'sync':
      return SyncOutlined
    case 'info':
      return InfoCircleOutlined
    default:
      return null
  }
})

const title = computed(() => {
  const parts = [props.presentation.description].filter(Boolean)
  if (props.showRawInDev && import.meta.env.DEV && props.presentation.rawCode) {
    parts.push(`原始值：${props.presentation.rawCode}`)
  }
  return parts.join(' · ') || undefined
})
</script>

<template>
  <Tooltip :title="title">
    <span
      class="sos-semantic-status"
      :data-semantic="presentation.semantic"
      :data-testid="'semantic-status-' + presentation.semantic"
      role="status"
    >
      <component :is="icon" v-if="icon" aria-hidden="true" class="sos-semantic-status__icon" />
      <span class="sos-semantic-status__label">{{ presentation.label }}</span>
      <span v-if="presentation.semantic === 'shadow'" class="sos-semantic-status__shadow">
        影子/非正式
      </span>
    </span>
  </Tooltip>
</template>

<style scoped>
.sos-semantic-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: 12px;
  line-height: 1.4;
  padding: 2px 8px;
  border-radius: var(--sos-radius-pill, 999px);
  border: 1px solid var(--sos-color-status-neutral-border);
  color: var(--sos-color-status-neutral-fg);
  background: var(--sos-color-status-neutral-bg);
}
.sos-semantic-status__icon {
  font-size: 12px;
}
.sos-semantic-status__shadow {
  margin-left: 2px;
  font-size: 12px;
}
.sos-semantic-status[data-semantic='info'] {
  color: var(--sos-color-status-info-fg);
  background: var(--sos-color-status-info-bg);
  border-color: var(--sos-color-status-info-border);
}
.sos-semantic-status[data-semantic='success'] {
  color: var(--sos-color-status-success-fg);
  background: var(--sos-color-status-success-bg);
  border-color: var(--sos-color-status-success-border);
}
.sos-semantic-status[data-semantic='warning'],
.sos-semantic-status[data-semantic='stale'] {
  color: var(--sos-color-status-warning-fg);
  background: var(--sos-color-status-warning-bg);
  border-color: var(--sos-color-status-warning-border);
}
.sos-semantic-status[data-semantic='critical'] {
  color: var(--sos-color-status-critical-fg);
  background: var(--sos-color-status-critical-bg);
  border-color: var(--sos-color-status-critical-border);
}
.sos-semantic-status[data-semantic='offline'] {
  color: var(--sos-color-status-offline-fg);
  background: var(--sos-color-status-offline-bg);
  border-color: var(--sos-color-status-offline-border);
}
.sos-semantic-status[data-semantic='shadow'] {
  color: var(--sos-color-status-shadow-fg);
  background: var(--sos-color-status-shadow-bg);
  border-color: var(--sos-color-status-shadow-border);
}
</style>
