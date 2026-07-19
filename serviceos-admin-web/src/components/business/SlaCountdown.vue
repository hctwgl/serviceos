<script setup lang="ts">
import { computed } from 'vue'
import { Tag } from 'ant-design-vue'

const props = defineProps<{
  status?: string | null
  displayText?: string | null
  dueAt?: string | null
}>()

const color = computed(() => {
  if (props.status === 'BREACHED') return 'error'
  if (props.status === 'RUNNING') return 'warning'
  if (props.status === 'MET' || props.status === 'MET_LATE') return 'success'
  return 'default'
})

const text = computed(() => props.displayText || '暂无时效')
</script>

<template>
  <Tag :color="color" data-testid="sla-countdown">{{ text }}</Tag>
</template>
