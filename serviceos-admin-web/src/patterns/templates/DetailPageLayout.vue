<script setup lang="ts">
import PageContainer from '../PageContainer.vue'
import StickyActionBar from '../StickyActionBar.vue'

defineProps<{
  title: string
  description?: string
  eyebrow?: string
  stickyNote?: string
  showSticky?: boolean
}>()
</script>

<template>
  <PageContainer
    :title="title"
    :description="description"
    :eyebrow="eyebrow"
    data-template="detail"
  >
    <template #back>
      <slot name="back" />
    </template>
    <template #status>
      <slot name="status" />
    </template>
    <template #subtitle>
      <slot name="subtitle" />
    </template>
    <template #secondary-actions>
      <slot name="secondary-actions" />
    </template>
    <template #primary-action>
      <slot name="primary-action" />
    </template>
    <template #feedback>
      <slot name="feedback" />
    </template>

    <div class="detail-page">
      <section v-if="$slots.summary" class="detail-page__summary" data-testid="detail-summary">
        <slot name="summary" />
      </section>
      <section v-if="$slots.progress" class="detail-page__progress" data-testid="detail-progress">
        <slot name="progress" />
      </section>
      <section v-if="$slots.risk" class="detail-page__risk" data-testid="detail-risk">
        <slot name="risk" />
      </section>
      <section class="detail-page__tabs">
        <slot />
      </section>
      <StickyActionBar v-if="showSticky !== false && $slots['sticky-actions']" :note="stickyNote">
        <template #secondary>
          <slot name="sticky-secondary" />
        </template>
        <slot name="sticky-actions" />
      </StickyActionBar>
    </div>
  </PageContainer>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-page__summary,
.detail-page__progress,
.detail-page__risk,
.detail-page__tabs {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light, #eaedf0);
  border-radius: var(--sos-radius-md, 8px);
  padding: 16px 20px;
}
</style>
