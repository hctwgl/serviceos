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
      component: () => import('./shell/AdminShell.vue'),
      children: [
        { path: '', redirect: '/workbench' },
        { path: 'workbench', name: 'workbench', component: () => import('./pages/WorkbenchPage.vue') },
        { path: 'work-orders', name: 'work-orders', component: () => import('./pages/WorkOrderDirectoryPage.vue') },
        { path: 'work-orders/:id', name: 'work-order', component: () => import('./pages/WorkOrderWorkspacePage.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.path === '/auth/callback' || currentSession().authenticated) return true
  await beginLogin(to.fullPath)
  return false
})
