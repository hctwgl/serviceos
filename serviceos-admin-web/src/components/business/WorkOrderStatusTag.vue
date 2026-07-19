<script setup lang="ts">
import { computed } from 'vue'
import { Tag } from 'ant-design-vue'
import { statusLabel } from '../../product/statusLabels'

const props = defineProps<{ status: string }>()

const label = computed(() => statusLabel(props.status))
const color = computed(() => {
  switch (props.status) {
    case 'FULFILLED':
    case 'COMPLETED':
    case 'APPROVED':
    case 'CLOSED':
      return 'success'
    case 'ACTIVE':
    case 'IN_PROGRESS':
    case 'CLAIMED':
    case 'OPEN':
      return 'processing'
    case 'BREACHED':
    case 'REJECTED':
    case 'CANCELLED':
    case 'QUARANTINED':
      return 'error'
    case 'SUSPENDED':
    case 'BLOCKED':
      return 'warning'
    default:
      return 'default'
  }
})
</script>

<template>
  <Tag :color="color">{{ label }}</Tag>
</template>
