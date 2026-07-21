<script setup lang="ts">
import { Space } from 'ant-design-vue'

defineProps<{
  /** 总数文案，例如「共 120 条」或「已加载 20 条，还有更多」 */
  countLabel?: string
}>()
</script>

<template>
  <div class="list-toolbar" data-testid="list-toolbar">
    <div class="list-toolbar__left">
      <span v-if="countLabel" class="list-toolbar__count" data-testid="list-toolbar-count">{{
        countLabel
      }}</span>
      <slot name="leading" />
    </div>
    <Space class="list-toolbar__right" wrap>
      <slot name="views" />
      <slot name="actions" />
    </Space>
  </div>
</template>

<style scoped>
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 0;
}
.list-toolbar__count {
  font-size: 13px;
  color: var(--sos-color-text-secondary, #4b5563);
}
.list-toolbar__left,
.list-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
