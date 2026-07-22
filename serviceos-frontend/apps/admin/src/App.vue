<script setup lang="ts">
import { ConfigProvider, serviceOsZhCN } from '@serviceos/design-system'
import { useAntdDesignTokens } from '@vben/hooks'

/**
 * Ant Design Vue 与 Vben 布局必须读取同一套运行时 Token。此前 ServiceOS 另外维护了一套
 * ThemeConfig，导致侧栏、页面容器和业务组件像是两个不同产品。
 */
const { tokens } = useAntdDesignTokens()
</script>

<template>
  <ConfigProvider :locale="serviceOsZhCN" :theme="{ token: tokens }">
    <RouterView />
  </ConfigProvider>
</template>
