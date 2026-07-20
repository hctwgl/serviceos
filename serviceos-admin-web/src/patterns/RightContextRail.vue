<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <aside class="sos-right-rail" data-testid="right-context-rail" :aria-label="title || '上下文说明'">
    <header v-if="title || $slots.title" class="sos-right-rail__header">
      <slot name="title">
        <h2>{{ title }}</h2>
      </slot>
    </header>
    <div class="sos-right-rail__body">
      <slot />
    </div>
  </aside>
</template>

<style scoped>
.sos-right-rail {
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  padding: 14px 16px;
  min-width: 260px;
}
.sos-right-rail__header h2 {
  margin: 0 0 12px;
  font-size: var(--sos-font-size-lg);
  font-weight: var(--sos-font-weight-semibold);
  color: var(--sos-color-text-primary);
}
.sos-right-rail__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-size: var(--sos-font-size-md);
  color: var(--sos-color-text-secondary);
}
</style>
