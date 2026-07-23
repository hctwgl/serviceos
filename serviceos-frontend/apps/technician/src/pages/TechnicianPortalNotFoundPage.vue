<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section data-testid="technician-not-found">
    <h2>页面不存在或功能已被调整</h2>
    <p>请从今日任务重新进入。</p>
    <RouterLink to="/technician-portal/task-feed">返回今日任务</RouterLink>
  </section>
</template>
