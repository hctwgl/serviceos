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
        <span class="sos-business-progress__index">
          {{ step.status === 'done' ? '✓' : index + 1 }}
        </span>
        <span class="sos-business-progress__label">{{ step.label }}</span>
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
  gap: 0;
  overflow-x: auto;
}
.sos-business-progress__step {
  position: relative;
  display: grid;
  justify-items: center;
  align-content: start;
  gap: 5px;
  min-width: 112px;
  padding: 0 10px;
  color: var(--sos-color-text-secondary);
  font-size: 13px;
}
.sos-business-progress__step:not(:last-child)::after {
  position: absolute;
  top: 13px;
  left: calc(50% + 17px);
  width: calc(100% - 34px);
  height: 1px;
  background: var(--sos-color-border-default);
  content: '';
}
.sos-business-progress__step[data-status='done'] {
  color: var(--sos-primary-600);
}
.sos-business-progress__step[data-status='done']:not(:last-child)::after {
  background: var(--sos-primary-500);
}
.sos-business-progress__step[data-status='current'] {
  color: var(--sos-primary-700);
  font-weight: 600;
}
.sos-business-progress__step[data-status='blocked'] {
  color: var(--sos-color-status-critical-fg);
}
.sos-business-progress__index {
  position: relative;
  z-index: 1;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sos-color-surface-card);
  border: 1px solid currentColor;
  font-size: 12px;
}
.sos-business-progress__step[data-status='current'] .sos-business-progress__index {
  background: var(--sos-primary-600);
  color: #fff;
}
.sos-business-progress__label {
  white-space: nowrap;
}
.sos-business-progress__hint,
.sos-business-progress__empty {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
</style>
