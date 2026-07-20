<script setup lang="ts">
import PageContainer from '../PageContainer.vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <PageContainer :title="title" :description="description" data-template="configuration">
    <template #status>
      <slot name="status" />
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

    <div class="config-page">
      <section v-if="$slots.context" class="config-page__panel" data-testid="config-context">
        <h2>配置上下文</h2>
        <slot name="context" />
      </section>
      <section v-if="$slots.version" class="config-page__panel" data-testid="config-version">
        <h2>版本摘要</h2>
        <slot name="version" />
      </section>
      <section class="config-page__panel" data-testid="config-tabs">
        <slot />
      </section>
      <section v-if="$slots.validation" class="config-page__panel" data-testid="config-validation">
        <h2>校验结果</h2>
        <slot name="validation" />
      </section>
      <section v-if="$slots.publish" class="config-page__panel config-page__publish" data-testid="config-publish">
        <h2>发布流程</h2>
        <p class="hint">配置发布必须使用专用流程，不能用普通确认框完成。</p>
        <slot name="publish" />
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.config-page__panel {
  background: var(--sos-color-surface-card, #fff);
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px 20px;
}
.config-page__panel h2 {
  margin: 0 0 12px;
  font-size: 15px;
}
.config-page__publish {
  border-color: var(--sos-color-status-warning-border);
}
.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--sos-color-text-secondary);
}
</style>
