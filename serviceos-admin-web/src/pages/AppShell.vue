<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
import ErrorBoundary from '../components/ErrorBoundary.vue'

/** 将服务端导航按运营场景分组；技术菜单收入「运维工具」。 */
const SECTION_RULES: Array<{ title: string; match: RegExp }> = [
  { title: '工作台', match: /WORKBENCH|SEARCH/i },
  { title: '工单运营', match: /WORK_?ORDER|REVIEW|CORRECTION/i },
  { title: '服务履约', match: /TASK|APPOINTMENT|VISIT|SLA/i },
  { title: '质量与时效', match: /EXCEPTION|SLA/i },
  { title: '基础资料', match: /PROJECT|NETWORK|TECHNICIAN|ORGANIZATION|USER/i },
  { title: '系统管理', match: /ROLE|GRANT|CONFIGURATION|PREFERENCE/i },
]

const DEVOPS_PAGE_IDS = new Set([
  'ADMIN.INTEGRATION.OUTBOUND',
  'ADMIN.INTEGRATION.INBOUND',
  'ADMIN.INTEGRATION.DETAIL',
  'ADMIN.INTEGRATION.INBOUND.DETAIL',
  'ADMIN.INTEGRATION.CANONICAL.DETAIL',
  'ADMIN.PORTAL.STUBS',
  'ADMIN.TOKEN',
])

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
const networkPortalUrl = import.meta.env.VITE_NETWORK_PORTAL_URL?.trim()
  || (import.meta.env.DEV ? 'http://localhost:5174' : '')
const technicianPortalUrl = import.meta.env.VITE_TECHNICIAN_PORTAL_URL?.trim()
  || (import.meta.env.DEV ? 'http://localhost:5175' : '')

const TEST_IDS: Record<string, string> = {
  'ADMIN.WORKBENCH': 'nav-workbench',
  'ADMIN.SEARCH': 'nav-search',
  'ADMIN.USER.DIRECTORY': 'nav-users',
  'ADMIN.ORGANIZATION.DIRECTORY': 'nav-organizations',
  'ADMIN.NETWORK.DIRECTORY': 'nav-networks',
  'ADMIN.TECHNICIAN.DIRECTORY': 'nav-technicians',
  'ADMIN.ROLE.DIRECTORY': 'nav-roles',
  'ADMIN.GRANT.DIRECTORY': 'nav-grants',
}

const showDevTools = import.meta.env.DEV

const groupedNav = computed(() => {
  const groups = new Map<string, typeof nav.value.items>()
  for (const item of nav.value.items) {
    if (DEVOPS_PAGE_IDS.has(item.pageId)) {
      continue
    }
    const section =
      SECTION_RULES.find((rule) => rule.match.test(item.pageId))?.title ?? '其他'
    const list = groups.get(section) ?? []
    list.push(item)
    groups.set(section, list)
  }
  return [...groups.entries()]
})

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
      <h1>ServiceOS 管理端</h1>
      <p class="hint">按工作场景组织导航。菜单可见性来自服务端，写操作仍由 Capability 校验。</p>
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
      <section v-for="[section, items] in groupedNav" :key="section" class="nav-section">
        <h2>{{ section }}</h2>
        <RouterLink
          v-for="item in items"
          :key="item.pageId"
          :to="routePathFor(item)"
          :data-testid="TEST_IDS[item.pageId] ?? `nav-${item.routeKey.replaceAll('/', '-')}`"
          :data-page-id="item.pageId"
        >
          {{ item.title }}
        </RouterLink>
      </section>
      <section class="nav-section">
        <h2>工单运营</h2>
        <RouterLink to="/work-orders/golden-path" data-testid="nav-golden-path">工单全流程演练</RouterLink>
      </section>
      <section class="nav-section">
        <h2>系统管理</h2>
        <RouterLink to="/settings/preferences" data-testid="nav-ui-preferences">界面偏好</RouterLink>
        <RouterLink v-if="showDevTools" to="/system/demo-data" data-testid="nav-demo-data">演示数据管理</RouterLink>
        <RouterLink to="/settings/token">身份登录</RouterLink>
      </section>
      <section v-if="showDevTools" class="nav-section">
        <h2>运维与开发者工具</h2>
        <RouterLink to="/work-orders/lookup">按 ID 打开</RouterLink>
        <RouterLink to="/integration/inbound">入站队列</RouterLink>
        <RouterLink to="/integration/outbound">外发队列</RouterLink>
        <RouterLink to="/portal-stubs" data-testid="nav-portal-stubs">Portal 诊断</RouterLink>
        <a v-if="networkPortalUrl" :href="networkPortalUrl" data-testid="nav-network-portal">网点端</a>
        <a v-if="technicianPortalUrl" :href="technicianPortalUrl" data-testid="nav-technician-portal">师傅端</a>
      </section>
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
      <ErrorBoundary scope="shell-page">
        <RouterView />
      </ErrorBoundary>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sos-sider-width, 216px) 1fr;
  min-height: 100vh;
  font-family: var(--sos-font-stack, Inter, system-ui, sans-serif);
  color: var(--sos-text-primary, #1f2937);
  background: var(--sos-bg-page, #f4f6f8);
}
:global(html.theme-dark) .shell {
  color: #f0f4f8;
  background: var(--sos-primary-900, #0b2f49);
}
:global(html.density-compact) .content {
  padding: 12px 16px;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 16px 12px;
  background: var(--sos-bg-card, #fff);
  color: var(--sos-text-primary, #1f2937);
  border-right: 1px solid var(--sos-border-default, #dfe3e8);
}
.nav h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--sos-primary-800, #103b5b);
}
.nav-section {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  margin-top: 0.35rem;
}
.nav-section h2 {
  margin: 0.35rem 0 0.15rem;
  font-size: 12px;
  font-weight: 600;
  color: var(--sos-text-tertiary, #7b8494);
}
.hint {
  margin: 0 0 0.5rem;
  font-size: 12px;
  color: var(--sos-text-tertiary, #7b8494);
}
.context {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 13px;
}
.context select {
  padding: 0.35rem;
  border: 1px solid var(--sos-border-default, #dfe3e8);
  border-radius: 4px;
}
.stale,
.error {
  margin: 0;
  font-size: 12px;
}
.stale {
  color: var(--sos-warning, #c97b13);
}
.error {
  color: var(--sos-danger, #d14343);
}
.nav a {
  color: var(--sos-text-secondary, #4b5563);
  text-decoration: none;
  padding: 8px 12px;
  border-radius: 4px;
  border-left: 3px solid transparent;
}
.nav a:hover {
  background: var(--sos-bg-hover, #f2f6f9);
}
.nav a.router-link-active {
  background: var(--sos-primary-100, #e9f2f8);
  color: var(--sos-text-primary, #1f2937);
  border-left-color: var(--sos-primary-700, #174a6e);
}
.recent {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--sos-divider, #edf0f2);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.recent h2 {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--sos-text-tertiary, #7b8494);
}
.recent-empty {
  margin: 0;
  font-size: 12px;
  color: var(--sos-text-tertiary, #7b8494);
}
.recent-item {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  font-size: 13px;
}
.recent-type {
  color: var(--sos-text-tertiary, #7b8494);
  font-size: 11px;
}
.recent-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-refresh {
  margin-top: 0.25rem;
  align-self: flex-start;
  background: transparent;
  border: 1px solid var(--sos-border-default, #dfe3e8);
  color: var(--sos-text-secondary, #4b5563);
  border-radius: 4px;
  padding: 0.2rem 0.45rem;
  font-size: 12px;
  cursor: pointer;
}
.content {
  padding: var(--sos-content-padding-y, 16px) var(--sos-content-padding-x, 24px);
}
</style>
