<script setup lang="ts">
import { ConfigProvider, serviceOsTheme, serviceOsZhCN } from '@serviceos/design-system'
</script>

<template>
  <ConfigProvider :locale="serviceOsZhCN" :theme="serviceOsTheme">
    <RouterView />
  </ConfigProvider>
</template>
