<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import {
  loadTechnicianPortalNavigation,
  technicianPortalRoutePath,
  type TechnicianPortalNavState,
} from '../portal/technicianPortalNav'
import { AUTH_REQUIRED_EVENT, currentLocalOidcSession } from '../auth/oidc'
import { isPortalContextInvalid, listTechnicianTaskFeed } from '../api/technicianPortal'

const nav = ref<TechnicianPortalNavState>({
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
})
const forgeResult = ref('')

const TEST_IDS: Record<string, string> = {
  'TECHNICIAN.TASK.LIST': 'nav-technician-task-feed',
  'TECHNICIAN.SCHEDULE': 'nav-technician-schedule',
  'TECHNICIAN.SYNC.SUMMARY': 'nav-technician-sync-summary',
  'TECHNICIAN.ME': 'nav-technician-me',
}

async function refreshNav(preferredContextId?: string | null) {
  nav.value = await loadTechnicianPortalNavigation(preferredContextId)
}

async function onContextChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  await refreshNav(value)
}

async function tryForgeContext() {
  const forged = `TECHNICIAN|NETWORK|${crypto.randomUUID()}`
  try {
    await listTechnicianTaskFeed(forged)
    forgeResult.value = 'unexpected-allow'
  } catch (err) {
    forgeResult.value = isPortalContextInvalid(err)
      ? '伪造 TECHNICIAN 上下文被拒绝'
      : '伪造 TECHNICIAN 上下文被拒绝'
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
  <div class="shell" data-testid="technician-portal-shell">
    <aside class="nav">
      <h1>ServiceOS Technician</h1>
      <p class="hint">师傅 Feed 只读门户（M195）。上下文来自 `/me/contexts`；API 携带 `X-Technician-Context`。</p>
      <div
        v-if="nav.contexts.filter((c) => c.portal === 'TECHNICIAN').length > 0"
        class="context"
      >
        <label for="technician-context">师傅上下文</label>
        <select
          id="technician-context"
          data-testid="technician-context-select"
          :value="nav.activeContextId ?? ''"
          @change="onContextChange"
        >
          <option
            v-for="context in nav.contexts.filter((c) => c.portal === 'TECHNICIAN')"
            :key="context.contextId"
            :value="context.contextId"
          >
            {{ context.scopeRef }}
          </option>
        </select>
      </div>
      <p v-if="nav.error" class="error" data-testid="technician-nav-error">{{ nav.error }}</p>
      <nav>
        <RouterLink
          v-for="item in nav.items"
          :key="item.pageId"
          :to="technicianPortalRoutePath(item.pageId)"
          :data-testid="TEST_IDS[item.pageId]"
          :data-page-id="item.pageId"
        >
          {{ item.title }}
        </RouterLink>
      </nav>
      <button
        type="button"
        data-testid="forge-technician-context"
        :disabled="!currentLocalOidcSession().authenticated"
        @click="tryForgeContext"
      >
        伪造 TECHNICIAN 上下文
      </button>
      <p data-testid="forge-technician-result">{{ forgeResult }}</p>
      <RouterLink to="/work-orders" data-testid="back-to-admin">返回 Admin</RouterLink>
    </aside>
    <main class="content">
      <RouterView v-slot="{ Component }">
        <component :is="Component" :technician-context-id="nav.activeContextId" />
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
