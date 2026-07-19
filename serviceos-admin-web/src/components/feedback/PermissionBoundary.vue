<script setup lang="ts">
import { Alert } from 'ant-design-vue'

defineProps<{
  readonlyMode?: boolean
  reason?: string | null
}>()
</script>

<template>
  <div class="permission-boundary">
    <Alert
      v-if="readonlyMode"
      type="info"
      show-icon
      :message="reason || '当前为只读模式，无法提交终审'"
      style="margin-bottom: 12px"
    />
    <slot />
  </div>
</template>
