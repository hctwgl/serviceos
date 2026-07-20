<script setup lang="ts">
import PageContainer from '../PageContainer.vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <PageContainer :title="title" :description="description" data-template="workbench">
    <template #secondary-actions>
      <slot name="secondary-actions" />
    </template>
    <template #primary-action>
      <slot name="primary-action" />
    </template>
    <template #feedback>
      <slot name="feedback" />
    </template>

    <div class="workbench">
      <section v-if="$slots.summary" class="workbench__summary" data-testid="workbench-summary">
        <slot name="summary" />
      </section>
      <section class="workbench__primary" data-testid="workbench-primary">
        <h2>我的待办</h2>
        <slot name="primary-queue" />
      </section>
      <div class="workbench__risk-row">
        <section class="workbench__panel workbench__panel--risk" data-testid="workbench-risk">
          <h2>风险摘要（P0/P1 · SLA · 重大异常）</h2>
          <slot name="risk-queue" />
        </section>
        <section class="workbench__panel" data-testid="workbench-today">
          <h2>今日跟进</h2>
          <slot name="today-queue" />
        </section>
      </div>
      <section class="workbench__panel" data-testid="workbench-recent">
        <h2>最近处理</h2>
        <slot name="recent-activity" />
      </section>
      <slot />
    </div>
  </PageContainer>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.workbench__summary {
  margin: 0;
}
.workbench__primary {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px 20px;
}
.workbench__primary h2,
.workbench__panel h2 {
  margin: 0 0 12px;
  font-size: 15px;
}
.workbench__risk-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}
@media (max-width: 1024px) {
  .workbench__risk-row {
    grid-template-columns: 1fr;
  }
}
.workbench__panel {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px 20px;
}
.workbench__panel--risk {
  border-color: var(--sos-color-status-warning-border);
}
</style>
