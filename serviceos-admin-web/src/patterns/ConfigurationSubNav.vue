<script setup lang="ts">
export type ConfigurationNavItem = {
  key: string
  label: string
  disabled?: boolean
  badge?: string
}

const props = defineProps<{
  items: ConfigurationNavItem[]
  activeKey: string
}>()

const emit = defineEmits<{
  select: [key: string]
}>()

function onSelect(item: ConfigurationNavItem) {
  if (item.disabled) return
  emit('select', item.key)
}
</script>

<template>
  <nav class="sos-config-subnav" data-testid="configuration-subnav" aria-label="配置二级导航">
    <button
      v-for="item in props.items"
      :key="item.key"
      type="button"
      class="sos-config-subnav__item"
      :class="{
        'is-active': item.key === activeKey,
        'is-disabled': item.disabled,
      }"
      :aria-current="item.key === activeKey ? 'page' : undefined"
      :disabled="item.disabled"
      @click="onSelect(item)"
    >
      <span>{{ item.label }}</span>
      <span v-if="item.badge" class="sos-config-subnav__badge">{{ item.badge }}</span>
    </button>
  </nav>
</template>

<style scoped>
.sos-config-subnav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  min-width: 180px;
}
.sos-config-subnav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  border-radius: var(--sos-radius-md);
  padding: 8px 10px;
  font-size: var(--sos-font-size-md);
  color: var(--sos-color-text-secondary);
  cursor: pointer;
  min-height: 36px;
}
.sos-config-subnav__item:hover:not(.is-disabled) {
  background: var(--sos-color-surface-hover);
  color: var(--sos-color-text-primary);
}
.sos-config-subnav__item.is-active {
  background: var(--sos-primary-100);
  color: var(--sos-primary-700);
  font-weight: var(--sos-font-weight-medium);
}
.sos-config-subnav__item.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.sos-config-subnav__badge {
  font-size: 11px;
  color: var(--sos-color-text-tertiary);
}
</style>
