<script setup lang="ts">
export type SummaryStripItem = {
  key: string
  label: string
  value: string
  hint?: string
  tone?: 'default' | 'success' | 'warning' | 'critical' | 'info'
}

defineProps<{
  items: SummaryStripItem[]
}>()
</script>

<template>
  <section class="sos-summary-strip" data-testid="summary-strip" aria-label="概览">
    <div
      v-for="item in items"
      :key="item.key"
      class="sos-summary-strip__item"
      :data-key="item.key"
      :data-testid="`summary-strip-${item.key}`"
      :data-tone="item.tone || 'default'"
    >
      <div class="sos-summary-strip__label">{{ item.label }}</div>
      <div class="sos-summary-strip__value">{{ item.value }}</div>
      <div v-if="item.hint" class="sos-summary-strip__hint">{{ item.hint }}</div>
    </div>
  </section>
</template>

<style scoped>
.sos-summary-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 1px;
  background: var(--sos-color-border-default);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  overflow: hidden;
  margin-bottom: var(--sos-space-4);
}
.sos-summary-strip__item {
  background: var(--sos-color-surface-card);
  padding: 12px 14px;
  min-height: 72px;
}
.sos-summary-strip__label {
  font-size: var(--sos-font-size-xs);
  color: var(--sos-color-text-tertiary);
  margin-bottom: 4px;
}
.sos-summary-strip__value {
  font-size: var(--sos-font-size-lg);
  font-weight: var(--sos-font-weight-semibold);
  color: var(--sos-color-text-primary);
  word-break: break-word;
}
.sos-summary-strip__hint {
  margin-top: 4px;
  font-size: var(--sos-font-size-xs);
  color: var(--sos-color-text-secondary);
}
.sos-summary-strip__item[data-tone='success'] .sos-summary-strip__value {
  color: var(--sos-color-status-success-fg);
}
.sos-summary-strip__item[data-tone='warning'] .sos-summary-strip__value {
  color: var(--sos-color-status-warning-fg);
}
.sos-summary-strip__item[data-tone='critical'] .sos-summary-strip__value {
  color: var(--sos-color-status-critical-fg);
}
.sos-summary-strip__item[data-tone='info'] .sos-summary-strip__value {
  color: var(--sos-color-status-info-fg);
}
</style>
