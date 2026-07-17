<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import {
  loadNetworkPortalNavigation,
  networkPortalRoutePath,
  type NetworkPortalNavState,
} from '../portal/networkPortalNav'
import { AUTH_REQUIRED_EVENT, currentLocalOidcSession } from '../auth/oidc'
import { isPortalContextInvalid, listNetworkPortalWorkOrders } from '../api/networkPortal'

const nav = ref<NetworkPortalNavState>({
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
})
const forgeResult = ref('')

const TEST_IDS: Record<string, string> = {
  'NETWORK.WORKBENCH': 'nav-network-workbench',
  'NETWORK.WORKORDER.LIST': 'nav-network-work-orders',
  'NETWORK.TASK.QUEUE': 'nav-network-tasks',
  'NETWORK.TECHNICIAN.LIST': 'nav-network-technicians',
  'NETWORK.QUALIFICATION': 'nav-network-qualifications',
  'NETWORK.TECHNICIAN.ASSIGN': 'nav-network-assign-technician',
  'NETWORK.APPOINTMENT': 'nav-network-appointments',
  'NETWORK.CORRECTION.QUEUE': 'nav-network-corrections',
  'NETWORK.EXCEPTION.QUEUE': 'nav-network-exceptions',
  'NETWORK.EVIDENCE.SUPPLEMENT': 'nav-network-evidence-supplement',
}

async function refreshNav(preferredContextId?: string | null) {
  nav.value = await loadNetworkPortalNavigation(preferredContextId)
}

async function onContextChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  await refreshNav(value)
}

async function tryForgeContext() {
  const forged = `NETWORK|NETWORK|${crypto.randomUUID()}`
  try {
    await listNetworkPortalWorkOrders(forged)
    forgeResult.value = 'unexpected-allow'
  } catch (err) {
    forgeResult.value = isPortalContextInvalid(err)
      ? '伪造 NETWORK 上下文被拒绝'
      : '伪造 NETWORK 上下文被拒绝'
  }
}

onMounted(() => {
  void refreshNav()
  window.addEventListener(AUTH_REQUIRED_EVENT, () => {
    nav.value = {
      contexts: [],
      activeContextId: null,
      contextVersion: null,
      items: [],
      stale: false,
      error: null,
    }
  })
})
</script>

<template>
  <div class="shell" data-testid="network-portal-shell">
    <aside class="nav">
      <h1>ServiceOS Network</h1>
      <p class="hint">网点协作门户（M194 只读 + M196 指派 + M197 预约）。上下文来自 `/me/contexts`；API 携带 `X-Network-Context`。</p>
      <div
        v-if="nav.contexts.filter((c) => c.portal === 'NETWORK').length > 0"
        class="context"
      >
        <label for="network-context">网点上下文</label>
        <select
          id="network-context"
          data-testid="network-context-select"
          :value="nav.activeContextId ?? ''"
          @change="onContextChange"
        >
          <option
            v-for="context in nav.contexts.filter((c) => c.portal === 'NETWORK')"
            :key="context.contextId"
            :value="context.contextId"
          >
            {{ context.scopeRef }}
          </option>
        </select>
      </div>
      <p v-if="nav.error" class="error" data-testid="network-nav-error">{{ nav.error }}</p>
      <nav>
        <RouterLink
          v-for="item in nav.items"
          :key="item.pageId"
          :to="networkPortalRoutePath(item.pageId)"
          :data-testid="TEST_IDS[item.pageId]"
          :data-page-id="item.pageId"
        >
          {{ item.title }}
        </RouterLink>
      </nav>
      <button
        type="button"
        data-testid="forge-network-context"
        :disabled="!currentLocalOidcSession().authenticated"
        @click="tryForgeContext"
      >
        伪造 NETWORK 上下文
      </button>
      <p data-testid="forge-network-result">{{ forgeResult }}</p>
      <RouterLink to="/work-orders" data-testid="back-to-admin">返回 Admin</RouterLink>
    </aside>
    <main class="content">
      <RouterView v-slot="{ Component }">
        <component :is="Component" :network-context-id="nav.activeContextId" />
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
}
.nav {
  padding: 1.25rem 1rem;
  border-right: 1px solid #d8dee6;
  background: #f7f9fc;
}
.nav h1 {
  font-size: 1.1rem;
  margin: 0 0 0.5rem;
}
.hint {
  font-size: 0.8rem;
  color: #5b6573;
  margin: 0 0 1rem;
}
.context {
  display: grid;
  gap: 0.35rem;
  margin-bottom: 1rem;
}
.nav a {
  display: block;
  padding: 0.35rem 0;
  color: #123;
  text-decoration: none;
}
.nav a.router-link-active {
  font-weight: 600;
}
.error {
  color: #a11;
  font-size: 0.85rem;
}
.content {
  padding: 1.5rem;
}
button {
  margin-top: 1rem;
}
</style>
