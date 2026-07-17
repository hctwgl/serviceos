<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import {
  loadAdminPortalNavigation,
  routePathFor,
  type PortalNavState,
} from '../nav/portalNavigation'
import { AUTH_REQUIRED_EVENT, currentLocalOidcSession } from '../auth/oidc'
import { loadAndApplyUiPreferences } from '../preferences/uiPreferenceState'
import {
  listRecentResources,
  type RecentResourceItem,
} from '../api/recentResources'

const nav = ref<PortalNavState>({
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
})

const recentItems = ref<RecentResourceItem[]>([])
const recentError = ref<string | null>(null)

const TEST_IDS: Record<string, string> = {
  'ADMIN.SEARCH': 'nav-search',
  'ADMIN.USER.DIRECTORY': 'nav-users',
  'ADMIN.ORGANIZATION.DIRECTORY': 'nav-organizations',
  'ADMIN.NETWORK.DIRECTORY': 'nav-networks',
  'ADMIN.TECHNICIAN.DIRECTORY': 'nav-technicians',
  'ADMIN.ROLE.DIRECTORY': 'nav-roles',
  'ADMIN.GRANT.DIRECTORY': 'nav-grants',
}

async function refreshNav(preferredContextId?: string | null) {
  nav.value = await loadAdminPortalNavigation(preferredContextId)
}

async function refreshRecent() {
  if (!currentLocalOidcSession().authenticated) {
    recentItems.value = []
    recentError.value = null
    return
  }
  try {
    const page = await listRecentResources(10)
    recentItems.value = page.items
    recentError.value = null
  } catch (err) {
    recentError.value = err instanceof Error ? err.message : '最近访问加载失败'
    recentItems.value = []
  }
}

async function onContextChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  await refreshNav(value)
}

onMounted(() => {
  void refreshNav()
  if (currentLocalOidcSession().authenticated) {
    void loadAndApplyUiPreferences()
    void refreshRecent()
  }
  window.addEventListener(AUTH_REQUIRED_EVENT, () => {
    nav.value = {
      contexts: [],
      activeContextId: null,
      contextVersion: null,
      items: [],
      stale: false,
      error: null,
    }
    recentItems.value = []
    recentError.value = null
  })
  window.addEventListener('focus', () => {
    if (currentLocalOidcSession().authenticated) {
      void refreshNav(nav.value.activeContextId)
      void loadAndApplyUiPreferences()
      void refreshRecent()
    }
  })
})
</script>

<template>
  <div class="shell">
    <aside class="nav">
      <h1>ServiceOS Admin</h1>
      <p class="hint">运营外壳（M101～M188）。导航来自 `/me/navigation`，写操作仍走服务端命令与 Capability。</p>
      <div
        v-if="nav.contexts.filter((c) => c.portal === 'ADMIN').length > 1"
        class="context"
      >
        <label for="portal-context">上下文</label>
        <select
          id="portal-context"
          data-testid="portal-context-select"
          :value="nav.activeContextId ?? ''"
          @change="onContextChange"
        >
          <option
            v-for="context in nav.contexts.filter((c) => c.portal === 'ADMIN')"
            :key="context.contextId"
            :value="context.contextId"
          >
            {{ context.portal }} / {{ context.scopeRef }}
          </option>
        </select>
      </div>
      <p v-if="nav.stale" class="stale" data-testid="context-stale-banner">上下文已刷新（旧版本失效）</p>
      <p v-if="nav.error" class="error" data-testid="nav-error">{{ nav.error }}</p>
      <template v-for="item in nav.items" :key="item.pageId">
        <RouterLink
          :to="routePathFor(item)"
          :data-testid="TEST_IDS[item.pageId] ?? `nav-${item.routeKey.replaceAll('/', '-')}`"
          :data-page-id="item.pageId"
        >
          {{ item.title }}
        </RouterLink>
      </template>
      <RouterLink to="/work-orders/lookup">按 ID 打开</RouterLink>
      <RouterLink to="/settings/preferences" data-testid="nav-ui-preferences">界面偏好</RouterLink>
      <RouterLink to="/settings/token">身份登录</RouterLink>
      <RouterLink to="/portal-stubs" data-testid="nav-portal-stubs">Portal stubs</RouterLink>
      <RouterLink to="/network-portal" data-testid="nav-network-portal">Network Portal</RouterLink>
      <section class="recent" data-testid="recent-resources">
        <h2>最近访问</h2>
        <p v-if="recentError" class="error" data-testid="recent-error">{{ recentError }}</p>
        <p v-else-if="recentItems.length === 0" class="recent-empty" data-testid="recent-empty">暂无记录</p>
        <RouterLink
          v-for="item in recentItems"
          :key="`${item.resourceType}:${item.resourceId}`"
          :to="item.deepLink"
          class="recent-item"
          :data-testid="`recent-${item.resourceType}-${item.resourceId}`"
          :data-resource-type="item.resourceType"
          :data-resource-id="item.resourceId"
        >
          <span class="recent-type">{{ item.resourceType }}</span>
          <span class="recent-label">{{ item.displayRef }}</span>
        </RouterLink>
        <button
          type="button"
          class="recent-refresh"
          data-testid="recent-refresh"
          @click="refreshRecent"
        >
          刷新最近
        </button>
      </section>
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
  transition: background-color 0.2s ease, color 0.2s ease;
}
:global(html.theme-dark) .shell {
  color: #f0f4f8;
  background: #102a43;
}
:global(html.density-compact) .content {
  padding: 0.85rem;
}
:global(html.reduce-motion) .shell {
  transition: none;
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
.context {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
}
.context select {
  padding: 0.35rem;
}
.stale,
.error {
  margin: 0;
  font-size: 0.75rem;
}
.stale {
  color: #f0b429;
}
.error {
  color: #f86a6a;
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
.recent {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #486581;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.recent h2 {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: #bcccdc;
}
.recent-empty {
  margin: 0;
  font-size: 0.75rem;
  color: #9fb3c8;
}
.recent-item {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  font-size: 0.78rem;
}
.recent-type {
  color: #9fb3c8;
  font-size: 0.68rem;
}
.recent-label {
  color: #f0f4f8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-refresh {
  margin-top: 0.25rem;
  align-self: flex-start;
  background: transparent;
  border: 1px solid #627d98;
  color: #d9e2ec;
  border-radius: 4px;
  padding: 0.2rem 0.45rem;
  font-size: 0.72rem;
  cursor: pointer;
}
.content {
  padding: 1.5rem;
}
</style>
