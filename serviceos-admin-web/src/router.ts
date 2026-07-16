import { createRouter, createWebHistory } from 'vue-router'
import AppShell from './pages/AppShell.vue'
import ReviewQueuePage from './pages/ReviewQueuePage.vue'
import CorrectionQueuePage from './pages/CorrectionQueuePage.vue'
import OutboundQueuePage from './pages/OutboundQueuePage.vue'
import ExceptionQueuePage from './pages/ExceptionQueuePage.vue'
import TokenPage from './pages/TokenPage.vue'
import WorkOrderLookupPage from './pages/WorkOrderLookupPage.vue'
import WorkOrderWorkspacePage from './pages/WorkOrderWorkspacePage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/reviews' },
        { path: 'reviews', name: 'ADMIN.REVIEW.QUEUE', component: ReviewQueuePage },
        { path: 'corrections', name: 'ADMIN.CORRECTION.QUEUE', component: CorrectionQueuePage },
        { path: 'integration/outbound', name: 'ADMIN.INTEGRATION.OUTBOUND', component: OutboundQueuePage },
        { path: 'exceptions', name: 'ADMIN.EXCEPTION.QUEUE', component: ExceptionQueuePage },
        { path: 'work-orders', name: 'ADMIN.WORKORDER.LOOKUP', component: WorkOrderLookupPage },
        {
          path: 'work-orders/:id',
          name: 'ADMIN.WORKORDER.WORKSPACE',
          component: WorkOrderWorkspacePage,
        },
        { path: 'settings/token', name: 'ADMIN.TOKEN', component: TokenPage },
      ],
    },
  ],
})

