<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section data-testid="network-not-found">
    <h2>页面不存在或功能已被调整</h2>
    <p>请从工作台重新进入所需功能。</p>
    <RouterLink to="/network-portal/workbench">返回工作台</RouterLink>
  </section>
</template>
