<script setup lang="ts">
import { Button, Tooltip } from 'ant-design-vue'

defineProps<{
  label: string
  enabled: boolean
  reason?: string | null
  primary?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <Tooltip v-if="!enabled && reason" :title="reason">
    <Button
      type="primary"
      :disabled="true"
      :loading="loading"
      data-testid="allowed-action-button"
    >
      {{ label }}
    </Button>
  </Tooltip>
  <Button
    v-else
    :type="primary ? 'primary' : 'default'"
    :disabled="!enabled"
    :loading="loading"
    data-testid="allowed-action-button"
    @click="emit('click')"
  >
    {{ label }}
  </Button>
</template>
