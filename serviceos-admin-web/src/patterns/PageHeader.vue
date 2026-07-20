<script setup lang="ts">
/**
 * 经典专业风页面头：面包屑可选、标题、说明、主/次操作。
 * 不承载业务授权判断；动作由调用方按 allowed-actions 传入。
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="sos-page-header" data-testid="page-header">
    <div v-if="$slots.breadcrumb" class="sos-page-header__breadcrumb">
      <slot name="breadcrumb" />
    </div>
    <div class="sos-page-header__row">
      <div class="sos-page-header__titles">
        <h1 class="sos-page-header__title">{{ title }}</h1>
        <p v-if="description" class="sos-page-header__description">{{ description }}</p>
        <div v-if="$slots.meta" class="sos-page-header__meta">
          <slot name="meta" />
        </div>
      </div>
      <div class="sos-page-header__actions">
        <slot name="secondary-actions" />
        <slot name="primary-action" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.sos-page-header {
  display: flex;
  flex-direction: column;
  gap: var(--sos-space-2);
  margin-bottom: var(--sos-space-4);
}
.sos-page-header__breadcrumb {
  font-size: var(--sos-font-size-sm);
  color: var(--sos-color-text-tertiary);
}
.sos-page-header__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sos-space-4);
  flex-wrap: wrap;
}
.sos-page-header__title {
  margin: 0;
  font-size: var(--sos-font-size-title);
  font-weight: var(--sos-font-weight-semibold);
  color: var(--sos-color-text-primary);
  line-height: var(--sos-line-height-tight);
}
.sos-page-header__description {
  margin: 6px 0 0;
  font-size: var(--sos-font-size-md);
  color: var(--sos-color-text-secondary);
  max-width: 720px;
}
.sos-page-header__meta {
  margin-top: var(--sos-space-2);
  display: flex;
  flex-wrap: wrap;
  gap: var(--sos-space-2);
}
.sos-page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sos-space-2);
  align-items: center;
}
</style>
