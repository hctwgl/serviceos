<script setup lang="ts">
/**
 * 全局反馈宿主：承接 message/notification 容器锚点。
 * Ant Design Vue 4 使用静态 message，此组件预留可访问的 live region。
 */
</script>

<template>
  <div
    class="global-feedback-host"
    data-testid="global-feedback-host"
    aria-live="polite"
    aria-relevant="additions text"
  >
    <slot />
  </div>
</template>

<style scoped>
.global-feedback-host {
  position: relative;
  z-index: var(--sos-z-toast, 1500);
}
</style>
