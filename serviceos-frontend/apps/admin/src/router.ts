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
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.path === '/auth/callback' || currentSession().authenticated) return true
  await beginLogin(to.fullPath)
  return false
})
