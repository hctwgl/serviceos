<script setup lang="ts">
import { WarningOutlined } from '@serviceos/design-system'
defineProps<{ title?: string; detail: string }>()
</script>

<template>
  <section class="page-error" role="alert">
    <WarningOutlined />
    <div>
      <h2>{{ title ?? '页面暂时无法加载' }}</h2>
      <p>{{ detail }}</p>
      <p class="error-advice">请确认服务已启动并刷新页面；问题持续时，请将页面时间和操作步骤提供给技术支持。</p>
    </div>
  </section>
</template>
