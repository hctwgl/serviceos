<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Layout,
  Menu,
  Breadcrumb,
  Button,
  Dropdown,
  Avatar,
  Tooltip,
  Alert,
  Select,
  Typography,
} from 'ant-design-vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  UserOutlined,
  LogoutOutlined,
  BugOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons-vue'
import {
  loadAdminPortalNavigation,
  routePathFor,
  type PortalNavState,
} from '../nav/portalNavigation'
import {
  AUTH_REQUIRED_EVENT,
  clearLocalOidcSession,
  currentLocalOidcSession,
} from '../auth/oidc'
import { loadAndApplyUiPreferences } from '../preferences/uiPreferenceState'
import {
  listRecentResources,
  type RecentResourceItem,
} from '../api/recentResources'
import { getMe, type MeContext, type MeProfile } from '../api/me'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import ScopeBar from '../patterns/ScopeBar.vue'
import DeveloperDiagnosticsDrawer from '../patterns/DeveloperDiagnosticsDrawer.vue'
import GlobalFeedbackHost from '../patterns/GlobalFeedbackHost.vue'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'

const { Header, Sider, Content } = Layout

/**
 * 正式导航只消费服务端 section/pageId/route/order。
 * 开发诊断入口不进入正式侧栏；不得用正则或角色名猜测菜单归属。
 */
const NON_PRODUCT_PAGE_IDS = new Set([
  'ADMIN.PORTAL.STUBS',
  'ADMIN.TOKEN',
])

const COLLAPSE_KEY = 'serviceos.admin.siderCollapsed'

const nav = ref<PortalNavState>({
  contexts: [],
  activeContextId: null,
  contextVersion: null,
  items: [],
  stale: false,
  error: null,
})

const profile = ref<MeProfile | null>(null)
const profileAsOf = ref<string | null>(null)
const recentItems = ref<RecentResourceItem[]>([])
const recentError = ref<string | null>(null)
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
const routeProgress = ref(false)

const TEST_IDS: Record<string, string> = {
  'ADMIN.WORKBENCH': 'nav-workbench',
  'ADMIN.SEARCH': 'nav-search',
  'ADMIN.USER.DIRECTORY': 'nav-users',
  'ADMIN.ORGANIZATION.DIRECTORY': 'nav-organizations',
  'ADMIN.NETWORK.DIRECTORY': 'nav-networks',
  'ADMIN.TECHNICIAN.DIRECTORY': 'nav-technicians',
  'ADMIN.ROLE.DIRECTORY': 'nav-roles',
  'ADMIN.GRANT.DIRECTORY': 'nav-grants',
  'ADMIN.MASTERDATA.CATALOG': 'nav-master-data',
}

const diagnostics = useDeveloperDiagnostics()
const route = useRoute()
const router = useRouter()

const groupedNav = computed(() => {
  const groups = new Map<string, typeof nav.value.items>()
  const orderedItems = [...nav.value.items].sort((a, b) => a.order - b.order)
  for (const item of orderedItems) {
    if (NON_PRODUCT_PAGE_IDS.has(item.pageId)) continue
    const section = item.section?.trim() || '未分组'
    const list = groups.get(section) ?? []
    list.push(item)
    groups.set(section, list)
  }
  return [...groups.entries()]
})

const adminContexts = computed(() =>
  nav.value.contexts.filter((c) => c.portal === 'ADMIN'),
)

const activeContext = computed<MeContext | null>(() => {
  return (
    adminContexts.value.find((c) => c.contextId === nav.value.activeContextId) ??
    null
  )
})

const selectedNavKeys = computed(() => {
  const pageId = typeof route.meta.pageId === 'string' ? route.meta.pageId : route.name
  return pageId ? [String(pageId)] : []
})

const breadcrumbItems = computed(() => {
  const items: Array<{ title: string; path?: string }> = [
    { title: '工作台', path: '/workbench' },
  ]
  const pageTitle =
    nav.value.items.find((i) => i.pageId === route.meta.pageId || i.pageId === route.name)
      ?.title ??
    (typeof route.name === 'string' ? routeNameLabel(route.name) : '当前页')
  if (route.path !== '/workbench' && route.path !== '/') {
    items.push({ title: pageTitle })
  }
  return items
})

function routeNameLabel(name: string): string {
  const map: Record<string, string> = {
    'ADMIN.WORKORDER.LIST': '工单中心',
    'ADMIN.WORKORDER.WORKSPACE': '工单详情',
    'ADMIN.PROJECT.DETAIL': '项目详情',
    'ADMIN.PROJECT.LIST': '项目目录',
    'ADMIN.MASTERDATA.CATALOG': '主数据治理',
    'ADMIN.REVIEW.QUEUE': '审核队列',
    'ADMIN.CORRECTION.QUEUE': '整改跟踪',
    'ADMIN.TOKEN': '身份登录',
  }
  return map[name] ?? '当前页'
}

async function refreshNav(preferredContextId?: string | null) {
  nav.value = await loadAdminPortalNavigation(preferredContextId)
  if (nav.value.contextVersion) {
    diagnostics.pushDiagnostic({
      title: 'Portal 导航已加载',
      fields: {
        contextId: nav.value.activeContextId,
        contextVersion: nav.value.contextVersion,
        itemCount: nav.value.items.length,
        stale: nav.value.stale,
      },
    })
  }
}

async function refreshProfile() {
  if (!currentLocalOidcSession().authenticated) {
    profile.value = null
    profileAsOf.value = null
    return
  }
  try {
    const me = await getMe()
    profile.value = me
    profileAsOf.value = me.asOf
  } catch {
    profile.value = null
  }
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

async function refreshShell() {
  await Promise.all([
    refreshNav(nav.value.activeContextId),
    refreshProfile(),
    refreshRecent(),
  ])
}

async function onContextChange(value: string) {
  await refreshNav(value)
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

function logout() {
  clearLocalOidcSession()
  void router.push('/settings/token')
}

function goSearch() {
  void router.push('/search')
}

watch(
  () => route.fullPath,
  () => {
    routeProgress.value = true
    window.setTimeout(() => {
      routeProgress.value = false
    }, 280)
  },
)

onMounted(() => {
  void refreshShell()
  if (currentLocalOidcSession().authenticated) {
    void loadAndApplyUiPreferences()
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
    profile.value = null
  })
  window.addEventListener('focus', () => {
    if (currentLocalOidcSession().authenticated) {
      void refreshShell()
      void loadAndApplyUiPreferences()
    }
  })
})
</script>

<template>
  <Layout class="shell" data-testid="app-shell">
    <Sider
      class="shell__sider"
      collapsible
      :collapsed="collapsed"
      :trigger="null"
      :width="216"
      :collapsed-width="64"
      theme="light"
    >
      <div class="brand" data-testid="app-brand">
        <div class="brand__mark" aria-hidden="true">S</div>
        <div v-if="!collapsed" class="brand__text">
          <Typography.Title :level="5" class="brand__title">ServiceOS</Typography.Title>
          <p class="brand__subtitle">运营管理平台</p>
        </div>
      </div>

      <div v-if="adminContexts.length > 1 && !collapsed" class="context">
        <label class="sr-only" for="portal-context">运营上下文</label>
        <Select
          id="portal-context"
          data-testid="portal-context-select"
          class="context__select"
          :value="nav.activeContextId ?? undefined"
          :options="
            adminContexts.map((context) => ({
              value: context.contextId,
              label: `${context.portal} / ${context.scopeRef}`,
            }))
          "
          @change="(v) => onContextChange(String(v))"
        />
      </div>

      <Alert
        v-if="nav.stale"
        type="warning"
        show-icon
        class="shell-alert"
        data-testid="context-stale-banner"
        message="上下文已刷新，请确认当前范围"
      />
      <Alert
        v-if="nav.error"
        type="error"
        show-icon
        class="shell-alert"
        data-testid="nav-error"
        :message="nav.error"
      />

      <nav class="sider-nav" aria-label="主导航">
        <section v-for="[section, items] in groupedNav" :key="section" class="nav-section">
          <h2 v-if="!collapsed" class="nav-section__title">{{ section }}</h2>
          <Menu mode="inline" :selected-keys="selectedNavKeys" :inline-collapsed="collapsed">
            <Menu.Item v-for="item in items" :key="item.pageId">
              <RouterLink
                :to="routePathFor(item)"
                :data-testid="TEST_IDS[item.pageId] ?? `nav-${item.routeKey.replaceAll('/', '-')}`"
                :data-page-id="item.pageId"
              >
                {{ item.title }}
              </RouterLink>
            </Menu.Item>
          </Menu>
        </section>

      </nav>

      <section
        v-if="!collapsed"
        class="recent"
        data-testid="recent-resources"
        aria-label="最近访问"
      >
        <h2>最近访问</h2>
        <p v-if="recentError" class="error" data-testid="recent-error">{{ recentError }}</p>
        <p v-else-if="recentItems.length === 0" class="recent-empty" data-testid="recent-empty">
          暂无记录
        </p>
        <RouterLink
          v-for="item in recentItems"
          :key="`${item.resourceType}:${item.resourceId}`"
          :to="item.deepLink"
          class="recent-item"
          :data-testid="`recent-${item.resourceType}-${item.resourceId}`"
          :data-resource-type="item.resourceType"
          :data-resource-id="item.resourceId"
          :aria-label="`最近访问 ${item.resourceType}`"
        >
          <span class="recent-type" aria-hidden="true">{{ item.resourceType }}</span>
          <span class="recent-label" aria-hidden="true">{{ item.displayRef }}</span>
        </RouterLink>
        <Button
          size="small"
          data-testid="recent-refresh"
          aria-label="重新加载最近访问"
          @click="refreshRecent"
        >
          重新加载最近访问
        </Button>
      </section>

      <div class="sider-footer">
        <Tooltip :title="collapsed ? '展开导航' : '折叠导航'">
          <Button
            type="text"
            class="collapse-trigger"
            data-testid="sidebar-collapse-trigger"
            :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'"
            @click="toggleCollapsed"
          >
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </Button>
        </Tooltip>
      </div>
    </Sider>

    <Layout>
      <Header class="shell__header" data-testid="app-header">
        <div class="header__left">
          <Tooltip :title="collapsed ? '展开导航' : '折叠导航'">
            <Button
              type="text"
              class="header__icon-btn"
              data-testid="sidebar-trigger"
              :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'"
              @click="toggleCollapsed"
            >
              <MenuUnfoldOutlined v-if="collapsed" />
              <MenuFoldOutlined v-else />
            </Button>
          </Tooltip>
          <Breadcrumb class="header__breadcrumb" data-testid="app-breadcrumb">
            <Breadcrumb.Item v-for="(item, idx) in breadcrumbItems" :key="idx">
              <RouterLink v-if="item.path && idx < breadcrumbItems.length - 1" :to="item.path">
                {{ item.title }}
              </RouterLink>
              <span v-else>{{ item.title }}</span>
            </Breadcrumb.Item>
          </Breadcrumb>
        </div>

        <div class="header__center">
          <ScopeBar
            :profile="profile"
            :active-context="activeContext"
            :as-of="profileAsOf"
            freshness-status="UNKNOWN"
            @refresh="refreshShell"
          />
        </div>

        <div class="header__right">
          <Tooltip title="全局搜索">
            <Button
              type="text"
              class="header__icon-btn"
              aria-label="打开全局搜索"
              data-testid="global-search-entry"
              @click="goSearch"
            >
              <SearchOutlined />
            </Button>
          </Tooltip>
          <Tooltip title="帮助与产品说明">
            <Button
              type="text"
              class="header__icon-btn"
              aria-label="帮助"
              data-testid="help-entry"
              @click="router.push('/settings/preferences')"
            >
              <QuestionCircleOutlined />
            </Button>
          </Tooltip>
          <Tooltip v-if="diagnostics.canOpen.value" title="开发诊断">
            <Button
              type="text"
              class="header__icon-btn"
              aria-label="打开开发诊断"
              data-testid="diagnostics-entry"
              @click="diagnostics.openDrawer()"
            >
              <BugOutlined />
            </Button>
          </Tooltip>
          <Dropdown :trigger="['click']">
            <button type="button" class="user-entry" data-testid="user-menu-trigger" aria-label="用户菜单">
              <Avatar size="small">
                <template #icon><UserOutlined /></template>
              </Avatar>
              <span class="user-entry__meta">
                <strong>{{ profile?.displayName || '未登录' }}</strong>
                <small>
                  {{ activeContext?.scopeRef || '当前视角未加载' }}
                </small>
              </span>
            </button>
            <template #overlay>
              <Menu data-testid="user-menu">
                <Menu.Item key="profile" disabled>
                  {{ profile?.displayName || '未登录' }}
                </Menu.Item>
                <Menu.Item key="scope" disabled>
                  {{ activeContext ? `${activeContext.portal} / ${activeContext.scopeRef}` : '无可用上下文' }}
                </Menu.Item>
                <Menu.Divider />
                <Menu.Item key="logout" data-testid="user-logout" @click="logout">
                  <LogoutOutlined /> 退出登录
                </Menu.Item>
              </Menu>
            </template>
          </Dropdown>
        </div>
      </Header>

      <div
        v-if="routeProgress"
        class="route-progress"
        data-testid="route-progress"
        aria-hidden="true"
      />

      <Content class="shell__content">
        <GlobalFeedbackHost />
        <ErrorBoundary scope="shell-page">
          <RouterView />
        </ErrorBoundary>
      </Content>
    </Layout>

    <DeveloperDiagnosticsDrawer />
  </Layout>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: var(--sos-color-surface-page, #f4f6f8);
  font-family: var(--sos-font-stack);
}
.shell__sider {
  border-right: 1px solid var(--sos-color-border-default, #dfe3e8);
  background: var(--sos-color-surface-card, #fff) !important;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--sos-header-height, 56px);
  padding: 0 16px;
  border-bottom: 1px solid var(--sos-color-divider, #edf0f2);
}
.brand__mark {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--sos-primary-600);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  flex-shrink: 0;
}
.brand__title {
  margin: 0 !important;
  font-size: 14px !important;
  line-height: 1.2 !important;
  color: var(--sos-primary-800) !important;
}
.brand__subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
}
.context {
  padding: 8px 12px;
}
.context__select {
  width: 100%;
}
.shell-alert {
  margin: 8px 12px;
}
.sider-nav {
  padding-bottom: 12px;
}
.sider-nav :deep(.ant-menu-item-selected) {
  background: var(--sos-primary-100, #e9f2f8) !important;
}
.sider-nav :deep(.ant-menu-item-selected a),
.sider-nav :deep(.ant-menu-item-selected .ant-menu-title-content) {
  color: var(--sos-primary-800, #103b5b) !important;
  font-weight: 600;
}
.sider-nav :deep(.ant-menu-item a) {
  color: var(--sos-color-text-secondary, #4b5563);
}
.nav-section__title {
  margin: 12px 16px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
}
.recent {
  margin: 8px 12px 16px;
  padding-top: 12px;
  border-top: 1px solid var(--sos-color-divider, #edf0f2);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.recent h2 {
  margin: 0;
  font-size: 12px;
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
}
.recent-empty,
.error {
  margin: 0;
  font-size: 12px;
}
.error {
  color: var(--sos-color-status-critical-fg);
}
.recent-item {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  color: inherit;
  padding: 4px 6px;
  border-radius: 4px;
}
.recent-item:hover {
  background: var(--sos-color-surface-hover);
}
.recent-type {
  font-size: 11px;
  color: var(--sos-color-text-tertiary);
}
.recent-label {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sider-footer {
  position: sticky;
  bottom: 0;
  padding: 8px;
  background: var(--sos-color-surface-card);
  border-top: 1px solid var(--sos-color-divider);
}
.shell__header {
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--sos-header-height, 56px) !important;
  line-height: var(--sos-header-height, 56px) !important;
  padding: 0 16px !important;
  background: var(--sos-color-surface-card, #fff) !important;
  border-bottom: 1px solid var(--sos-color-border-default, #dfe3e8);
  position: sticky;
  top: 0;
  z-index: var(--sos-z-header, 1100);
}
.header__left,
.header__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.header__center {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.header__icon-btn {
  color: var(--sos-color-text-secondary) !important;
}
.header__breadcrumb {
  margin-left: 4px;
}
.header__breadcrumb :deep(a) {
  color: var(--sos-primary-700, #174a6e);
}
.header__breadcrumb :deep(.ant-breadcrumb-separator) {
  color: var(--sos-color-text-secondary, #4b5563);
}
.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.user-entry:hover {
  background: var(--sos-color-surface-hover);
}
.user-entry__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}
.user-entry__meta strong {
  font-size: 13px;
  color: var(--sos-color-text-primary);
}
.user-entry__meta small {
  font-size: 12px;
  color: var(--sos-color-text-secondary, #4b5563);
}
.route-progress {
  height: 2px;
  background: linear-gradient(
    90deg,
    var(--sos-primary-600),
    var(--sos-accent-500),
    var(--sos-primary-600)
  );
  background-size: 200% 100%;
  animation: route-progress var(--sos-motion-duration-slow) linear infinite;
}
@keyframes route-progress {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
.shell__content {
  padding: var(--sos-content-padding-y, 16px) var(--sos-content-padding-x, 24px);
  min-height: calc(100vh - var(--sos-header-height, 56px));
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
