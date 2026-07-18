<script setup lang="ts">
import { resolveNetworkEnvironment } from './environment'
import { computed, ref } from 'vue'
import { beginDevelopmentLogin, isDevelopmentLoginAvailable, logout } from './auth/session'
import { createNetworkApi } from './api/client'
import { loadNetworkSession, type NavigationItem, type NetworkSession } from './networkSession'

const environment = resolveNetworkEnvironment({
  mode: import.meta.env.MODE,
  apiBaseUrl: import.meta.env.VITE_SERVICEOS_API_BASE_URL,
  clientVersion: import.meta.env.VITE_SERVICEOS_CLIENT_VERSION,
})
const api = createNetworkApi(environment)
const session = ref<NetworkSession | null>(null)
const error = ref<string | null>(null)
const groupedNavigation = computed(() => (session.value?.navigation ?? []).reduce<Record<string, NavigationItem[]>>((groups, item) => {
  ;(groups[item.section] ??= []).push(item)
  return groups
}, {}))
async function refresh(contextId?: string) { try { error.value = null; session.value = await loadNetworkSession(api, contextId) } catch (cause) { session.value = null; error.value = cause instanceof Error ? cause.message : '上下文加载失败' } }
async function login() { await beginDevelopmentLogin() }
function signOut() { logout(); session.value = null }
</script>

<template>
  <div class="app-shell">
    <header class="shell-header">
      <p class="eyebrow">ServiceOS</p>
      <h1>网点协作 Portal</h1>
      <span class="environment">{{ environment.name }} · {{ environment.clientVersion }}</span>
    </header>
    <main class="shell-main">
      <section v-if="!session" class="boundary-card" aria-labelledby="foundation-title">
        <h2 id="foundation-title">连接网点上下文</h2>
        <p v-if="error" class="error">{{ error }}</p>
        <button v-if="isDevelopmentLoginAvailable()" type="button" @click="login">开发环境登录</button>
        <button type="button" @click="refresh()">加载当前会话</button>
        <p v-if="!isDevelopmentLoginAvailable()">正式身份接入尚未配置，应用失败关闭，不接受手工 Token。</p>
      </section>
      <template v-else>
        <aside class="context-card">
          <label for="network-context">网点上下文</label>
          <select id="network-context" :value="session.activeContextId" @change="refresh(($event.target as HTMLSelectElement).value)">
            <option v-for="context in session.contexts" :key="context.contextId" :value="context.contextId">{{ context.scopeRef }}</option>
          </select>
          <p>{{ session.capabilities.length }} 项服务端 Capability</p>
          <button type="button" @click="signOut">退出</button>
        </aside>
        <section class="boundary-card">
          <h2>服务端导航</h2>
          <section v-for="(items, section) in groupedNavigation" :key="section"><h3>{{ section }}</h3>
            <ul><li v-for="item in items" :key="item.pageId" :data-page-id="item.pageId">{{ item.title }}</li></ul>
          </section>
          <p>业务页面将在后续里程碑迁移；导航可见性不替代 API 授权。</p>
        </section>
      </template>
    </main>
  </div>
</template>
