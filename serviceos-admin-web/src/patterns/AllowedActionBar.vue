<script setup lang="ts">
export type AllowedActionItem = {
  code: string
  label: string
  disabled?: boolean
  primary?: boolean
}

defineProps<{
  actions: AllowedActionItem[]
  emptyText?: string
}>()

const emit = defineEmits<{
  select: [code: string]
}>()
</script>

<template>
  <div class="sos-allowed-action-bar" data-testid="allowed-action-bar">
    <template v-if="actions.length">
      <button
        v-for="action in actions"
        :key="action.code"
        type="button"
        class="sos-allowed-action-bar__btn"
        :class="{ primary: action.primary }"
        :disabled="action.disabled"
        @click="emit('select', action.code)"
      >
        {{ action.label }}
      </button>
    </template>
    <span v-else class="sos-allowed-action-bar__empty">{{ emptyText || '当前无可执行动作' }}</span>
  </div>
</template>

<style scoped>
.sos-allowed-action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.sos-allowed-action-bar__btn {
  border: 1px solid var(--sos-color-border-default);
  background: var(--sos-color-surface-card);
  color: var(--sos-color-text-primary);
  border-radius: var(--sos-radius-md);
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
}
.sos-allowed-action-bar__btn.primary {
  background: var(--sos-primary-600);
  border-color: var(--sos-primary-600);
  color: #fff;
}
.sos-allowed-action-bar__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.sos-allowed-action-bar__empty {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
</style>
