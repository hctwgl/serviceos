<script setup lang="ts">
import { Alert, Empty, Skeleton } from 'ant-design-vue'

defineProps<{
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyDescription?: string
}>()
</script>

<template>
  <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" />
  <Alert v-else-if="error" type="error" show-icon :message="error" />
  <Empty v-else-if="empty" :description="emptyDescription || '暂无数据'" />
  <slot v-else />
</template>
