<script setup lang="ts">
export type BusinessProgressStep = {
  key: string
  label: string
  status: 'done' | 'current' | 'upcoming' | 'blocked'
  hint?: string
}

defineProps<{
  title?: string
  steps: BusinessProgressStep[]
}>()
</script>

<template>
  <section class="sos-business-progress" data-testid="business-progress" :aria-label="title || '履约进度'">
    <h3 v-if="title">{{ title }}</h3>
    <ol v-if="steps.length" class="sos-business-progress__list">
      <li
        v-for="(step, index) in steps"
        :key="step.key"
        class="sos-business-progress__step"
        :data-status="step.status"
      >
        <span class="sos-business-progress__index">{{ index + 1 }}</span>
        <span class="sos-business-progress__label">{{ step.label }}</span>
        <span v-if="step.status === 'current'" class="sos-business-progress__badge">当前</span>
        <span v-if="step.hint" class="sos-business-progress__hint">{{ step.hint }}</span>
      </li>
    </ol>
    <p v-else class="sos-business-progress__empty">履约阶段尚未生成</p>
  </section>
</template>

<style scoped>
.sos-business-progress h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.sos-business-progress__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sos-business-progress__step {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--sos-color-border-default);
  border-radius: 999px;
  background: var(--sos-color-surface-subtle);
  color: var(--sos-color-text-secondary);
  font-size: 13px;
}
.sos-business-progress__step[data-status='done'] {
  border-color: var(--sos-color-status-success-border);
  background: var(--sos-color-status-success-bg);
  color: var(--sos-color-status-success-fg);
}
.sos-business-progress__step[data-status='current'] {
  border-color: var(--sos-primary-500);
  background: var(--sos-primary-100);
  color: var(--sos-primary-800);
  font-weight: 600;
}
.sos-business-progress__step[data-status='blocked'] {
  border-color: var(--sos-color-status-critical-border);
  background: var(--sos-color-status-critical-bg);
  color: var(--sos-color-status-critical-fg);
}
.sos-business-progress__index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sos-color-surface-card);
  border: 1px solid currentColor;
  font-size: 11px;
}
.sos-business-progress__badge {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--sos-primary-600);
  color: #fff;
}
.sos-business-progress__hint,
.sos-business-progress__empty {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
</style>
