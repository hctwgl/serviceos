<script setup lang="ts">
defineProps<{
  note?: string
}>()
</script>

<template>
  <div class="sticky-action-bar" data-testid="sticky-action-bar" role="region" aria-label="页面主操作">
    <p v-if="note" class="sticky-action-bar__note">{{ note }}</p>
    <div class="sticky-action-bar__actions">
      <slot name="secondary" />
      <slot />
    </div>
  </div>
</template>

<style scoped>
.sticky-action-bar {
  position: sticky;
  bottom: 0;
  z-index: var(--sos-z-sticky, 100);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-top: 16px;
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-default, #dfe3e8);
  border-radius: var(--sos-radius-md, 8px);
  box-shadow: var(--sos-elevation-2);
}
.sticky-action-bar__note {
  margin: 0;
  font-size: 13px;
  color: var(--sos-color-text-secondary);
}
.sticky-action-bar__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}
</style>
