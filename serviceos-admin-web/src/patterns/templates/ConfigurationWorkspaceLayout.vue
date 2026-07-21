<script setup lang="ts">
import PageHeader from '../PageHeader.vue'
import SummaryStrip, { type SummaryStripItem } from '../SummaryStrip.vue'
import ConfigurationSubNav, { type ConfigurationNavItem } from '../ConfigurationSubNav.vue'
import RightContextRail from '../RightContextRail.vue'

defineProps<{
  title: string
  description?: string
  summaryItems?: SummaryStripItem[]
  navItems: ConfigurationNavItem[]
  activeNavKey: string
  rightTitle?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  'nav-select': [key: string]
}>()
</script>

<template>
  <div class="sos-config-workspace" data-template="configuration-workspace">
    <PageHeader :title="title" :description="description">
      <template v-if="$slots.breadcrumb" #breadcrumb>
        <slot name="breadcrumb" />
      </template>
      <template v-if="$slots.meta" #meta>
        <slot name="meta" />
      </template>
      <template #secondary-actions>
        <slot name="secondary-actions" />
      </template>
      <template #primary-action>
        <slot name="primary-action" />
      </template>
    </PageHeader>

    <div v-if="$slots.feedback" class="sos-config-workspace__feedback">
      <slot name="feedback" />
    </div>

    <SummaryStrip v-if="summaryItems?.length" :items="summaryItems" />

    <div class="sos-config-workspace__body" :aria-busy="loading ? 'true' : undefined">
      <ConfigurationSubNav
        :items="navItems"
        :active-key="activeNavKey"
        @select="emit('nav-select', $event)"
      />
      <main class="sos-config-workspace__main">
        <slot />
      </main>
      <RightContextRail v-if="$slots.rail" :title="rightTitle">
        <slot name="rail" />
      </RightContextRail>
    </div>
  </div>
</template>

<style scoped>
.sos-config-workspace {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}
.sos-config-workspace__feedback {
  margin-bottom: var(--sos-space-3);
}
.sos-config-workspace__body {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) minmax(260px, 300px);
  gap: var(--sos-space-4);
  align-items: start;
}
.sos-config-workspace__main {
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  padding: 16px 20px;
  min-height: 420px;
}
@media (max-width: 1280px) {
  .sos-config-workspace__body {
    grid-template-columns: 180px minmax(0, 1fr);
  }
  .sos-config-workspace__body :deep(.sos-right-rail) {
    grid-column: 1 / -1;
  }
}
@media (max-width: 960px) {
  .sos-config-workspace__body {
    grid-template-columns: 1fr;
  }
}
</style>
