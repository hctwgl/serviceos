<script setup lang="ts">
import { RouterLink } from 'vue-router'

export type SummaryStripItem = {
  key: string
  label: string
  value: string | number
  hint?: string
  to?: string
  testId?: string
  tone?: 'default' | 'warning' | 'critical'
}

defineProps<{
  items: SummaryStripItem[]
  asOf?: string
}>()
</script>

<template>
  <section class="sos-summary-strip" data-testid="network-summary-strip" aria-label="概览指标">
    <p v-if="asOf" class="sos-summary-strip__asof">统计时间：{{ asOf }}</p>
    <ul class="sos-summary-strip__list">
      <li
        v-for="item in items"
        :key="item.key"
        class="sos-summary-strip__item"
        :data-tone="item.tone || 'default'"
        :data-testid="item.testId"
      >
        <RouterLink v-if="item.to" :to="item.to" class="sos-summary-strip__link">
          <span class="sos-summary-strip__label">{{ item.label }}</span>
          <strong class="sos-summary-strip__value">{{ item.value }}</strong>
          <span v-if="item.hint" class="sos-summary-strip__hint">{{ item.hint }}</span>
        </RouterLink>
        <template v-else>
          <span class="sos-summary-strip__label">{{ item.label }}</span>
          <strong class="sos-summary-strip__value">{{ item.value }}</strong>
          <span v-if="item.hint" class="sos-summary-strip__hint">{{ item.hint }}</span>
        </template>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.sos-summary-strip__asof {
  margin: 0 0 10px;
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.sos-summary-strip__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}
.sos-summary-strip__item {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 12px 14px;
}
.sos-summary-strip__item[data-tone='warning'] {
  border-color: var(--sos-color-status-warning-border);
  background: var(--sos-color-status-warning-bg);
}
.sos-summary-strip__item[data-tone='critical'] {
  border-color: var(--sos-color-status-critical-border);
  background: var(--sos-color-status-critical-bg);
}
.sos-summary-strip__link {
  display: grid;
  gap: 4px;
  color: inherit;
  text-decoration: none;
}
.sos-summary-strip__label {
  font-size: 12px;
  color: var(--sos-color-text-secondary);
}
.sos-summary-strip__value {
  font-size: 22px;
  line-height: 1.2;
  color: var(--sos-color-text-primary);
}
.sos-summary-strip__hint {
  font-size: 12px;
  color: var(--sos-color-text-tertiary);
}
</style>
