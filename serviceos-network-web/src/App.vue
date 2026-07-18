<script setup lang="ts">
import { resolveNetworkEnvironment } from './environment'

const environment = resolveNetworkEnvironment({
  mode: import.meta.env.MODE,
  apiBaseUrl: import.meta.env.VITE_SERVICEOS_API_BASE_URL,
  clientVersion: import.meta.env.VITE_SERVICEOS_CLIENT_VERSION,
})
</script>

<template>
  <div class="app-shell">
    <header class="shell-header">
      <p class="eyebrow">ServiceOS</p>
      <h1>网点协作 Portal</h1>
      <span class="environment">{{ environment.name }} · {{ environment.clientVersion }}</span>
    </header>
    <main class="shell-main">
      <section class="boundary-card" aria-labelledby="foundation-title">
        <h2 id="foundation-title">独立应用基础已就绪</h2>
        <p>本切片只建立独立 AppShell、构建和环境边界。登录、上下文、导航与业务页面将在后续里程碑接入。</p>
      </section>
    </main>
  </div>
</template>
