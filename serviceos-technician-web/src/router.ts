import { createRouter, createWebHistory } from 'vue-router'
import { defineComponent, h } from 'vue'
import TechnicianPortalTaskFeedPage from './pages/TechnicianPortalTaskFeedPage.vue'
import TechnicianPortalTaskDetailPage from './pages/TechnicianPortalTaskDetailPage.vue'
import TechnicianPortalSchedulePage from './pages/TechnicianPortalSchedulePage.vue'
import TechnicianPortalSyncSummaryPage from './pages/TechnicianPortalSyncSummaryPage.vue'
import TechnicianPortalMePage from './pages/TechnicianPortalMePage.vue'

const MigrationTestEntry = defineComponent({
  name: 'MigrationTestEntry',
  setup: () => () => h('div', { 'aria-hidden': 'true' }),
})

export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/technician-portal/task-feed' },
  // 双运行迁移期复用既有 Playwright 登录 helper；不显示 Admin 页面或接受手工 Token。
  { path: '/settings/token', component: MigrationTestEntry },
  { path: '/work-orders', component: MigrationTestEntry },
  { path: '/technician-portal', redirect: '/technician-portal/task-feed' },
  { path: '/technician-portal/task-feed', component: TechnicianPortalTaskFeedPage },
  { path: '/technician-portal/tasks/:id', component: TechnicianPortalTaskDetailPage },
  { path: '/technician-portal/schedule', component: TechnicianPortalSchedulePage },
  { path: '/technician-portal/sync-summary', component: TechnicianPortalSyncSummaryPage },
  { path: '/technician-portal/me', component: TechnicianPortalMePage },
] })

export function routeForPage(pageId: string) {
  const routes: Record<string, string> = {
    'TECHNICIAN.TASK.LIST': '/technician-portal/task-feed',
    'TECHNICIAN.TASK.DETAIL': '/technician-portal/task-feed',
    'TECHNICIAN.SCHEDULE': '/technician-portal/schedule',
    'TECHNICIAN.SYNC.SUMMARY': '/technician-portal/sync-summary',
    'TECHNICIAN.ME': '/technician-portal/me',
  }
  return routes[pageId] ?? null
}
