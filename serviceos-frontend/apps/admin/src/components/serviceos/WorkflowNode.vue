<script setup lang="ts">
import { CheckOutlined } from '@serviceos/design-system'
import type { WorkflowCanvasStage } from './types'

const props = withDefaults(defineProps<{
  stage: WorkflowCanvasStage
  selected?: boolean
}>(), {
  selected: false,
})

const emit = defineEmits<{
  select: [stage: WorkflowCanvasStage]
}>()
</script>

<template>
  <button
    type="button"
    class="sos-workflow-designer-node"
    :class="[`is-${stage.status ?? 'pending'}`, { 'is-selected': selected }]"
    :aria-pressed="selected"
    @click="emit('select', props.stage)"
  >
    <span class="sos-workflow-designer-node__index">
      <CheckOutlined v-if="stage.status === 'completed'" />
      <span v-else>{{ String(stage.sequence).padStart(2, '0') }}</span>
    </span>
    <span class="sos-workflow-designer-node__body">
      <strong>{{ stage.name }}</strong>
      <small>{{ stage.typeLabel }} · {{ stage.ownerLabel }}</small>
    </span>
    <span class="sos-workflow-designer-node__facts">
      <span>{{ stage.taskLabel }}</span>
      <span>{{ stage.slaLabel }}</span>
      <span>表单 {{ stage.formCount }} · 资料 {{ stage.evidenceCount }}</span>
    </span>
  </button>
</template>
