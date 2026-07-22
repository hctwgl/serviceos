import { createRouter, createWebHistory } from 'vue-router'
import { beginLogin, currentSession } from '@serviceos/auth-context'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth/callback',
      component: () => import('./pages/AuthCallbackPage.vue'),
    },
    {
      path: '/',
      component: () => import('./shell/ServiceOSBasicLayout.vue'),
      meta: { title: 'ServiceOS' },
      children: [
        { path: '', redirect: '/workbench' },
        { path: 'workbench', name: 'workbench', meta: { title: '工作台' }, component: () => import('./pages/WorkbenchPage.vue') },
        { path: 'work-orders', name: 'work-orders', meta: { title: '工单中心' }, component: () => import('./pages/WorkOrderDirectoryPage.vue') },
        { path: 'work-orders/:id', name: 'work-order', meta: { title: '工单详情', hideInMenu: true }, component: () => import('./pages/WorkOrderWorkspacePage.vue') },
        { path: 'system/users', name: 'users', meta: { title: '用户管理' }, component: () => import('./features/users/pages/UserDirectoryPage.vue') },
        { path: 'system/roles', name: 'roles', meta: { title: '角色与授权' }, component: () => import('./features/roles/pages/RoleDirectoryPage.vue') },
        { path: 'resources/organizations', name: 'organizations', meta: { title: '组织架构' }, component: () => import('./features/organizations/pages/OrganizationDirectoryPage.vue') },
        { path: 'resources/networks', name: 'service-networks', meta: { title: '服务网点' }, component: () => import('./features/resources/pages/ServiceNetworkDirectoryPage.vue') },
        { path: 'resources/technicians', name: 'technicians', meta: { title: '师傅档案' }, component: () => import('./features/resources/pages/TechnicianDirectoryPage.vue') },
        { path: 'clients', name: 'clients', meta: { title: '客户品牌' }, component: () => import('./features/projects/pages/ClientDirectoryPage.vue') },
        { path: 'projects', name: 'projects', meta: { title: '项目管理' }, component: () => import('./features/projects/pages/ProjectDirectoryPage.vue') },
        { path: 'projects/:id', name: 'project-workspace', meta: { title: '项目详情', hideInMenu: true }, component: () => import('./features/projects/pages/ProjectWorkspacePage.vue') },
        { path: 'projects/:id/fulfillment', name: 'project-fulfillment', meta: { title: '项目履约配置', hideInMenu: true }, component: () => import('./features/projects/pages/ProjectFulfillmentPage.vue') },
        { path: 'projects/:id/fulfillment/:profileId/draft', name: 'project-fulfillment-draft', meta: { title: '履约配置草稿', hideInMenu: true }, component: () => import('./features/projects/pages/ProjectFulfillmentDraftPage.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.path === '/auth/callback' || currentSession().authenticated) return true
  await beginLogin(to.fullPath)
  return false
})
