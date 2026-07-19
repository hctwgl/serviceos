<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { resolveTechnicianEnvironment } from './environment'
import { accessToken, beginLogin, isLoginAvailable, logout } from './auth/session'
import { createTechnicianApi } from './api/client'
import { loadTechnicianSession, type NavigationItem, type TechnicianSession } from './technicianSession'
import { routeForPage } from './router'

const environment = resolveTechnicianEnvironment({
  mode: import.meta.env.MODE,
  apiBaseUrl: import.meta.env.VITE_SERVICEOS_API_BASE_URL,
  clientVersion: import.meta.env.VITE_SERVICEOS_CLIENT_VERSION,
})
const api = createTechnicianApi(environment)
const session = ref<TechnicianSession | null>(null)
const error = ref<string | null>(null)
const forgeResult = ref('')
const switching = ref(false)
const loginLabel = import.meta.env.DEV ? '使用本地 Keycloak 登录' : '使用 OIDC 登录'
const devDiagnostics = import.meta.env.DEV
const DEMO_NETWORK_ID = 'd3500000-1000-4000-8000-000000000002'
function technicianContextLabel(scopeRef: string) {
  if (scopeRef === DEMO_NETWORK_ID) {
    return '张师傅 · 济南恒通新能源服务中心'
  }
  return `师傅网点 ${scopeRef.slice(0, 8)}…`
}
const testIds: Record<string, string> = {
  'TECHNICIAN.TASK.LIST': 'nav-technician-task-feed',
  'TECHNICIAN.SCHEDULE': 'nav-technician-schedule',
  'TECHNICIAN.SYNC.SUMMARY': 'nav-technician-sync-summary',
  'TECHNICIAN.ME': 'nav-technician-me',
}
const groupedNavigation = computed(() => (session.value?.navigation ?? []).reduce<Record<string, NavigationItem[]>>((groups, item) => {
  ;(groups[item.section] ??= []).push(item)
  return groups
}, {}))

async function refresh(contextId?: string) {
  try {
    error.value = null
    switching.value = true
    // 切换时先清空旧上下文页面，避免把上一位师傅或上一网点数据短暂显示为当前数据。
    session.value = null
    session.value = await loadTechnicianSession(api, contextId)
  } catch (cause) {
    session.value = null
    error.value = cause instanceof Error ? cause.message : 'TECHNICIAN 上下文加载失败'
  } finally {
    switching.value = false
  }
}

async function tryForgedContext() {
  try {
    await loadTechnicianSession(api, `TECHNICIAN|NETWORK|${crypto.randomUUID()}`)
    forgeResult.value = 'unexpected-allow'
  } catch {
    forgeResult.value = '伪造 TECHNICIAN 上下文被拒绝'
  }
}

async function login() {
  await beginLogin(window.location.pathname === '/settings/token' ? '/work-orders' : undefined)
}
function signOut() { logout(); session.value = null }
onMounted(() => { if (accessToken()) void refresh() })
</script>

<template>
  <div class="app-shell">
    <header class="shell-header">
      <div><p class="eyebrow">ServiceOS</p><h1>师傅在线工作台</h1></div>
      <span class="environment">{{ environment.name }} · {{ environment.clientVersion }}</span>
    </header>
    <main class="shell-main">
      <section v-if="!session" class="boundary-card" aria-labelledby="foundation-title">
        <h2 id="foundation-title">连接师傅上下文</h2>
        <p v-if="error" class="error" data-testid="technician-nav-error">{{ error }}</p>
        <p v-else-if="switching">正在重新验证当前责任和权限…</p>
        <button v-if="isLoginAvailable()" type="button" @click="login">{{ loginLabel }}</button>
        <button type="button" @click="refresh()">加载当前会话</button>
        <p v-if="!isLoginAvailable()">身份接入尚未配置，应用失败关闭，不接受手工 Token。</p>
      </section>
      <template v-else>
        <aside class="context-card" data-testid="technician-portal-shell">
          <label for="technician-context">当前身份</label>
          <select id="technician-context" data-testid="technician-context-select" :value="session.activeContextId" @change="refresh(($event.target as HTMLSelectElement).value)">
            <option v-for="context in session.contexts" :key="context.contextId" :value="context.contextId">
              {{ technicianContextLabel(context.scopeRef) }}
            </option>
          </select>
          <p>{{ session.capabilities.length }} 项服务端 Capability</p>
          <nav v-for="(items, section) in groupedNavigation" :key="section" :aria-label="section">
            <RouterLink v-for="item in items" :key="item.pageId" :to="routeForPage(item.pageId) ?? '/technician-portal/task-feed'" :data-testid="testIds[item.pageId]">
              {{ item.title }}<span v-if="!routeForPage(item.pageId)">（当前版本不可用）</span>
            </RouterLink>
          </nav>
          <template v-if="devDiagnostics">
            <button type="button" data-testid="forge-technician-context" @click="tryForgedContext">伪造 TECHNICIAN 上下文</button>
            <p data-testid="forge-technician-result">{{ forgeResult }}</p>
          </template>
          <button type="button" @click="signOut">退出</button>
        </aside>
        <section class="browser-boundary" data-testid="technician-browser-boundary">
          H5 仅承诺页面存续期内的在线能力，不承诺原生级定位、后台上传、杀进程恢复或完整离线可靠性。
        </section>
        <section class="page-content"><RouterView v-slot="{ Component }"><component :is="Component" :technician-context-id="session.activeContextId" /></RouterView></section>
      </template>
    </main>
  </div>
</template>
