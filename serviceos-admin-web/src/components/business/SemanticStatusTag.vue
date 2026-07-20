<script setup lang="ts">
import { computed } from 'vue'
import { Tag, Tooltip } from 'ant-design-vue'
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
import {
  antTagColorForSemantic,
  type SemanticStatusPresentation,
} from '../../presentation/semantic-status'

const props = defineProps<{
  presentation: SemanticStatusPresentation
  /** 开发环境可在 Tooltip 中附带原始码 */
  showRawInDev?: boolean
}>()

const color = computed(() => antTagColorForSemantic(props.presentation.semantic))

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
    <Tag
      :color="color"
      :data-semantic="presentation.semantic"
      :data-testid="'semantic-status-' + presentation.semantic"
      class="sos-semantic-status"
    >
      <component :is="icon" v-if="icon" aria-hidden="true" class="sos-semantic-status__icon" />
      <span>{{ presentation.label }}</span>
      <span v-if="presentation.semantic === 'shadow'" class="sos-semantic-status__shadow">
        影子/非正式
      </span>
    </Tag>
  </Tooltip>
</template>

<style scoped>
.sos-semantic-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
}
.sos-semantic-status__icon {
  font-size: 12px;
}
.sos-semantic-status__shadow {
  margin-left: 2px;
  font-size: 11px;
  opacity: 0.9;
}
</style>
