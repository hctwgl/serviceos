<script setup lang="ts">
import PageContainer from '../PageContainer.vue'
import QueryPanel from '../QueryPanel.vue'
import ListToolbar from '../ListToolbar.vue'

defineProps<{
  title: string
  description?: string
  loading?: boolean
  countLabel?: string
}>()

const emit = defineEmits<{ search: []; reset: [] }>()
</script>

<template>
  <PageContainer :title="title" :description="description" data-template="list">
    <template #secondary-actions>
      <slot name="secondary-actions" />
    </template>
    <template #primary-action>
      <slot name="primary-action" />
    </template>
    <template #feedback>
      <slot name="feedback" />
    </template>

    <div class="list-page">
      <slot name="view-bar" />
      <QueryPanel :loading="loading" @search="emit('search')" @reset="emit('reset')">
        <template #primary>
          <slot name="filters" />
        </template>
        <template v-if="$slots['more-filters']" #more>
          <slot name="more-filters" />
        </template>
      </QueryPanel>
      <ListToolbar :count-label="countLabel">
        <template #views>
          <slot name="toolbar-views" />
        </template>
        <template #actions>
          <slot name="toolbar-actions" />
        </template>
      </ListToolbar>
      <div class="list-page__table">
        <slot />
      </div>
      <div v-if="$slots.pagination" class="list-page__pagination">
        <slot name="pagination" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.list-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.list-page__table {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light, #eaedf0);
  border-radius: var(--sos-radius-md, 8px);
  padding: 8px;
  overflow: auto;
}
.list-page__pagination {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0;
}
</style>
