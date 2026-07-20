<script setup lang="ts">
/**
 * 专用复杂流程全页：改派、终审、配置发布、范围调整、批量等高风险多步骤操作。
 * 禁止用普通 Modal 承载。
 */
import PageContainer from '../PageContainer.vue'
import StickyActionBar from '../StickyActionBar.vue'

defineProps<{
  title: string
  description?: string
  stepLabel?: string
  stickyNote?: string
}>()
</script>

<template>
  <PageContainer :title="title" :description="description" data-template="dedicated-flow">
    <template #back>
      <slot name="back" />
    </template>
    <template #subtitle>
      <p v-if="stepLabel" class="flow-step" data-testid="flow-step">{{ stepLabel }}</p>
      <slot name="subtitle" />
    </template>
    <template #feedback>
      <slot name="feedback" />
    </template>

    <div class="flow-page">
      <aside v-if="$slots.rail" class="flow-page__rail" data-testid="flow-rail">
        <slot name="rail" />
      </aside>
      <main class="flow-page__main">
        <slot />
      </main>
      <aside v-if="$slots.context" class="flow-page__context" data-testid="flow-context">
        <slot name="context" />
      </aside>
    </div>
    <StickyActionBar :note="stickyNote">
      <template #secondary>
        <slot name="sticky-secondary" />
      </template>
      <slot name="sticky-actions" />
    </StickyActionBar>
  </PageContainer>
</template>

<style scoped>
.flow-step {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--sos-color-text-secondary);
}
.flow-page {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 280px;
  gap: 16px;
}
@media (max-width: 1280px) {
  .flow-page {
    grid-template-columns: minmax(0, 1fr);
  }
}
.flow-page__rail,
.flow-page__main,
.flow-page__context {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px 20px;
}
</style>
