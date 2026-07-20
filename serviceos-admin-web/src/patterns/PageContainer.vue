<script setup lang="ts">
/**
 * 统一页面容器：标题区 + 操作区 + 反馈 + 内容。
 * 业务页不得再自定义完全不同的顶部结构。
 */
defineProps<{
  title: string
  description?: string
  /** 状态区插槽旁的额外说明，禁止放完整 UUID */
  eyebrow?: string
}>()
</script>

<template>
  <section class="page-container" data-testid="page-container">
    <header class="page-container__header">
      <div class="page-container__title-area">
        <div class="page-container__leading">
          <slot name="back" />
          <div class="page-container__titles">
            <p v-if="eyebrow" class="page-container__eyebrow">{{ eyebrow }}</p>
            <div class="page-container__title-row">
              <h1 class="page-container__title">{{ title }}</h1>
              <slot name="status" />
            </div>
            <p v-if="description" class="page-container__desc">{{ description }}</p>
            <slot name="subtitle" />
          </div>
        </div>
        <div class="page-container__actions">
          <slot name="secondary-actions" />
          <slot name="primary-action" />
        </div>
      </div>
      <div v-if="$slots.feedback" class="page-container__feedback">
        <slot name="feedback" />
      </div>
    </header>
    <div class="page-container__content">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: var(--sos-space-4, 16px);
  min-width: 0;
}
.page-container__header {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light, #eaedf0);
  border-radius: var(--sos-radius-md, 8px);
  padding: var(--sos-space-4, 16px) var(--sos-space-6, 24px);
  box-shadow: var(--sos-elevation-1);
}
.page-container__title-area {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.page-container__leading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}
.page-container__eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
}
.page-container__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.page-container__title {
  margin: 0;
  font-size: var(--sos-font-size-xl, 20px);
  font-weight: 600;
  color: var(--sos-color-text-primary, #1f2937);
  line-height: 1.3;
}
.page-container__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--sos-color-text-secondary, #4b5563);
  max-width: 720px;
}
.page-container__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}
.page-container__feedback {
  margin-top: 12px;
}
.page-container__content {
  min-width: 0;
}
</style>
