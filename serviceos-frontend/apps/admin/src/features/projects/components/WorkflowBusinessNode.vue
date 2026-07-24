<script setup lang="ts">
import type { ProjectFulfillmentNodeDraft } from '@serviceos/api-client'

import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps<{
  data: ProjectFulfillmentNodeDraft
  selected?: boolean
}>()

const labels: Record<ProjectFulfillmentNodeDraft['nodeType'], string> = {
  CONDITION: '条件判断',
  END: '结束',
  EVENT_WAIT: '事件等待',
  HUMAN_TASK: '人工作业',
  REVIEW: '审核',
  START: '开始',
  SYSTEM_ACTION: '系统动作',
}

const missing = computed(() => {
  if (props.data.nodeType === 'HUMAN_TASK' || props.data.nodeType === 'REVIEW') {
    return [
      Object.keys(props.data.task).length ? null : '任务',
      props.data.responsibilityRole ? null : '责任',
    ].filter(Boolean)
  }
  if (props.data.nodeType === 'SYSTEM_ACTION') {
    return Object.keys(props.data.systemAction).length ? [] : ['动作配置']
  }
  if (props.data.nodeType === 'EVENT_WAIT') {
    return Object.keys(props.data.eventWait).length ? [] : ['等待规则']
  }
  if (props.data.nodeType === 'CONDITION') {
    return Object.keys(props.data.condition).length ? [] : ['条件规则']
  }
  return []
})
</script>

<template>
  <div
    class="workflow-business-node"
    :class="[
      `is-${data.nodeType.toLowerCase().replace('_', '-')}`,
      { 'is-selected': selected, 'is-incomplete': missing.length },
    ]"
  >
    <Handle
      v-if="data.nodeType !== 'START'"
      type="target"
      :position="Position.Top"
      class="workflow-node-handle"
    />
    <span class="workflow-node-icon" aria-hidden="true">
      {{ data.nodeType === 'START' ? '▶' : data.nodeType === 'END' ? '■' : data.nodeType === 'CONDITION' ? '◇' : data.nodeType === 'EVENT_WAIT' ? '⌛' : data.nodeType === 'SYSTEM_ACTION' ? '⚙' : data.nodeType === 'REVIEW' ? '✓' : '●' }}
    </span>
    <span class="workflow-node-copy">
      <strong>{{ data.nodeName }}</strong>
      <small>{{ labels[data.nodeType] }}<template v-if="data.responsibilityRole"> · {{ data.responsibilityRole }}</template></small>
    </span>
    <span v-if="missing.length" class="workflow-node-missing">{{ missing.length }}</span>
    <span v-else-if="!['START', 'END', 'CONDITION'].includes(data.nodeType)" class="workflow-node-ready">完整</span>
    <Handle
      v-if="data.nodeType !== 'END'"
      type="source"
      :position="Position.Bottom"
      class="workflow-node-handle"
    />
  </div>
</template>
