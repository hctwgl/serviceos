<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="shell">
    <aside class="nav">
      <h1>ServiceOS Admin</h1>
      <p class="hint">运营外壳（M101～M163）。写操作仅走服务端 allowed-actions/命令。</p>
      <RouterLink to="/reviews">审核队列</RouterLink>
      <RouterLink to="/corrections">整改跟踪</RouterLink>
      <RouterLink to="/tasks">任务目录</RouterLink>
      <RouterLink to="/sla">SLA 工作台</RouterLink>
      <RouterLink to="/exceptions">运营异常</RouterLink>
      <RouterLink to="/integration/inbound">入站队列</RouterLink>
      <RouterLink to="/integration/outbound">外发交付</RouterLink>
      <RouterLink to="/work-orders">工单目录</RouterLink>
      <RouterLink to="/projects">项目目录</RouterLink>
      <RouterLink to="/work-orders/lookup">按 ID 打开</RouterLink>
      <RouterLink to="/settings/token">身份登录</RouterLink>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
  font-family: Inter, system-ui, sans-serif;
  color: #102a43;
  background: #f5f7fa;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  background: #243b53;
  color: #f0f4f8;
}
.nav h1 {
  margin: 0;
  font-size: 1.1rem;
}
.hint {
  margin: 0 0 0.5rem;
  font-size: 0.8rem;
  color: #9fb3c8;
}
.nav a {
  color: #d9e2ec;
  text-decoration: none;
  padding: 0.4rem 0.55rem;
  border-radius: 6px;
}
.nav a.router-link-active {
  background: #334e68;
  color: #fff;
}
.content {
  padding: 1.5rem;
}
</style>
