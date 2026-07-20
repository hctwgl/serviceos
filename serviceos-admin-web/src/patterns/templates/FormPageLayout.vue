<script setup lang="ts">
import PageContainer from '../PageContainer.vue'
import StickyActionBar from '../StickyActionBar.vue'

defineProps<{
  title: string
  description?: string
  stickyNote?: string
}>()
</script>

<template>
  <PageContainer :title="title" :description="description" data-template="form">
    <template #feedback>
      <slot name="feedback" />
    </template>
    <div class="form-page">
      <div class="form-page__sections">
        <slot />
      </div>
      <aside v-if="$slots.impact" class="form-page__impact" data-testid="form-impact">
        <h2>影响预览</h2>
        <slot name="impact" />
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
.form-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}
@media (max-width: 1024px) {
  .form-page {
    grid-template-columns: 1fr;
  }
}
.form-page__sections,
.form-page__impact {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px 20px;
}
.form-page__impact h2 {
  margin: 0 0 12px;
  font-size: 15px;
}
</style>
