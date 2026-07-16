import { createRouter, createWebHistory } from 'vue-router'
import AppShell from './pages/AppShell.vue'
import ReviewQueuePage from './pages/ReviewQueuePage.vue'
import ReviewCaseDetailPage from './pages/ReviewCaseDetailPage.vue'
import CorrectionQueuePage from './pages/CorrectionQueuePage.vue'
import CorrectionCaseDetailPage from './pages/CorrectionCaseDetailPage.vue'
import OutboundQueuePage from './pages/OutboundQueuePage.vue'
import OutboundDeliveryDetailPage from './pages/OutboundDeliveryDetailPage.vue'
import ExceptionQueuePage from './pages/ExceptionQueuePage.vue'
import TokenPage from './pages/TokenPage.vue'
import WorkOrderLookupPage from './pages/WorkOrderLookupPage.vue'
import WorkOrderDirectoryPage from './pages/WorkOrderDirectoryPage.vue'
import WorkOrderWorkspacePage from './pages/WorkOrderWorkspacePage.vue'
import TaskDirectoryPage from './pages/TaskDirectoryPage.vue'
import TaskDetailPage from './pages/TaskDetailPage.vue'
import SlaQueuePage from './pages/SlaQueuePage.vue'
import SlaInstanceDetailPage from './pages/SlaInstanceDetailPage.vue'
import ProjectDirectoryPage from './pages/ProjectDirectoryPage.vue'
import ProjectDetailPage from './pages/ProjectDetailPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/reviews' },
        { path: 'reviews', name: 'ADMIN.REVIEW.QUEUE', component: ReviewQueuePage },
        { path: 'reviews/:id', name: 'ADMIN.REVIEW.DETAIL', component: ReviewCaseDetailPage },
        { path: 'corrections', name: 'ADMIN.CORRECTION.QUEUE', component: CorrectionQueuePage },
        { path: 'corrections/:id', name: 'ADMIN.CORRECTION.DETAIL', component: CorrectionCaseDetailPage },
        { path: 'integration/outbound', name: 'ADMIN.INTEGRATION.OUTBOUND', component: OutboundQueuePage },
        {
          path: 'integration/outbound/:id',
          name: 'ADMIN.INTEGRATION.DETAIL',
          component: OutboundDeliveryDetailPage,
        },
        { path: 'exceptions', name: 'ADMIN.EXCEPTION.QUEUE', component: ExceptionQueuePage },
        { path: 'tasks', name: 'ADMIN.TASK.QUEUE', component: TaskDirectoryPage },
        { path: 'tasks/:id', name: 'ADMIN.TASK.DETAIL', component: TaskDetailPage },
        { path: 'sla', name: 'ADMIN.SLA.QUEUE', component: SlaQueuePage },
        { path: 'sla/:id', name: 'ADMIN.SLA.DETAIL', component: SlaInstanceDetailPage },
        { path: 'projects', name: 'ADMIN.PROJECT.LIST', component: ProjectDirectoryPage },
        { path: 'projects/:id', name: 'ADMIN.PROJECT.DETAIL', component: ProjectDetailPage },
        { path: 'work-orders', name: 'ADMIN.WORKORDER.LIST', component: WorkOrderDirectoryPage },
        { path: 'work-orders/lookup', name: 'ADMIN.WORKORDER.LOOKUP', component: WorkOrderLookupPage },
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
