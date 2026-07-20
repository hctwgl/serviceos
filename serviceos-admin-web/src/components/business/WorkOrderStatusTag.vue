<script setup lang="ts">
import { computed } from 'vue'
import { presentWorkOrderStatus } from '../../presentation/work-order-status.presenter'
import SemanticStatusTag from './SemanticStatusTag.vue'

const props = defineProps<{ status: string }>()

const presentation = computed(() => presentWorkOrderStatus(props.status))
</script>

<template>
  <SemanticStatusTag :presentation="presentation" :show-raw-in-dev="true" />
</template>
