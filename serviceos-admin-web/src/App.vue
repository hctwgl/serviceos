<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { RouterView } from 'vue-router'
import ErrorBoundary from './components/ErrorBoundary.vue'
import { serviceOsTheme } from './app/app-theme'
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="serviceOsTheme">
    <ErrorBoundary scope="app">
      <RouterView />
    </ErrorBoundary>
  </ConfigProvider>
</template>
