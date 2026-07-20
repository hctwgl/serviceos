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
  <!-- 外层包装保证 data-testid 稳定落在 DOM（Ant 组件透传不一律可靠）。 -->
  <div v-if="loading" data-testid="async-content-loading">
    <Skeleton active :paragraph="{ rows: 4 }" />
  </div>
  <div v-else-if="error" data-testid="async-content-error">
    <Alert type="error" show-icon :message="error" />
  </div>
  <div v-else-if="empty" data-testid="async-content-empty">
    <Empty :description="emptyDescription || '暂无数据'" />
  </div>
  <slot v-else />
</template>
