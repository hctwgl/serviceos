<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { accessToken, beginLogin, isLoginAvailable, logout } from './auth/session'
import { loadNetworkSession, type NavigationItem, type NetworkSession } from './networkSession'
import { RouterLink, RouterView } from 'vue-router'
import { routeForPage } from './router'

const loginLabel = import.meta.env.DEV ? '使用本地 Keycloak 登录' : '使用 OIDC 登录'
const devDiagnostics = import.meta.env.DEV
const session = ref<NetworkSession | null>(null)
const error = ref<string | null>(null)
const forgeResult = ref('')
const DEMO_NETWORK_ID = 'd3500000-1000-4000-8000-000000000002'
function networkContextLabel(scopeRef: string) {
  if (scopeRef === DEMO_NETWORK_ID) {
    return '济南恒通新能源服务中心'
  }
  return `网点 ${scopeRef.slice(0, 8)}…`
}
const groupedNavigation = computed(() => (session.value?.navigation ?? []).reduce<Record<string, NavigationItem[]>>((groups, item) => {
  ;(groups[item.section] ??= []).push(item)
  return groups
}, {}))
async function refresh(contextId?: string) { try { error.value = null; session.value = await loadNetworkSession(contextId) } catch (cause) { session.value = null; error.value = cause instanceof Error ? cause.message : '上下文加载失败' } }
async function tryForgedContext() {
  try {
    await loadNetworkSession(`NETWORK|NETWORK|${crypto.randomUUID()}`)
    forgeResult.value = 'unexpected-allow'
  } catch {
    forgeResult.value = '伪造 NETWORK 上下文被拒绝'
  }
}
async function login() {
  await beginLogin()
}
function signOut() { logout(); session.value = null }
onMounted(() => { if (accessToken()) void refresh() })
</script>

<template>
  <div class="app-shell">
    <header class="shell-header">
      <p class="eyebrow">ServiceOS</p>
      <h1>网点协作 Portal</h1>
      <span class="environment">网点协作端</span>
    </header>
    <main class="shell-main">
      <section v-if="!session" class="boundary-card" aria-labelledby="foundation-title">
        <h2 id="foundation-title">连接网点上下文</h2>
        <p v-if="error" class="error">{{ error }}</p>
        <button
          v-if="error && accessToken()"
          type="button"
          data-testid="network-switch-account"
          @click="signOut"
        >
          退出并切换账号
        </button>
        <button v-if="isLoginAvailable()" type="button" @click="login">
          {{ loginLabel }}
        </button>
        <button type="button" @click="refresh()">加载当前会话</button>
        <p v-if="!isLoginAvailable()">身份接入尚未配置，应用失败关闭，不接受手工 Token。</p>
      </section>
      <template v-else>
        <aside class="context-card" data-testid="network-portal-shell">
          <label for="network-context">当前网点</label>
          <select id="network-context" :value="session.activeContextId" @change="refresh(($event.target as HTMLSelectElement).value)">
            <option v-for="context in session.contexts" :key="context.contextId" :value="context.contextId">
              {{ networkContextLabel(context.scopeRef) }}
            </option>
          </select>
          <p>{{ session.capabilities.length }} 项服务端 Capability</p>
          <template v-if="devDiagnostics">
            <button type="button" data-testid="forge-network-context" @click="tryForgedContext">伪造 NETWORK 上下文</button>
            <p data-testid="forge-network-result">{{ forgeResult }}</p>
          </template>
          <button type="button" @click="signOut">退出</button>
        </aside>
        <section class="boundary-card">
          <h2>服务端导航</h2>
          <section v-for="(items, section) in groupedNavigation" :key="section">
            <h3>{{ section }}</h3>
            <ul>
              <li v-for="item in items" :key="item.pageId" :data-page-id="item.pageId">
                <RouterLink v-if="routeForPage(item.pageId)" :to="routeForPage(item.pageId)!">{{ item.title }}</RouterLink>
                <span v-else>{{ item.title }}（当前版本不可用）</span>
              </li>
            </ul>
          </section>
          <p>导航可见性不替代 API 授权；所有业务请求仍由服务端按当前 Network Context 鉴权。</p>
        </section>
        <section class="page-content"><RouterView v-slot="{ Component }"><component :is="Component" :network-context-id="session.activeContextId" /></RouterView></section>
      </template>
    </main>
  </div>
</template>
