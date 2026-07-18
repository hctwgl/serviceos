import { createRouter, createWebHistory } from 'vue-router'
import NetworkPortalWorkbenchPage from './pages/NetworkPortalWorkbenchPage.vue'
import NetworkPortalWorkOrdersPage from './pages/NetworkPortalWorkOrdersPage.vue'
import NetworkPortalWorkOrderWorkspacePage from './pages/NetworkPortalWorkOrderWorkspacePage.vue'
import NetworkPortalTasksPage from './pages/NetworkPortalTasksPage.vue'
import NetworkPortalTechniciansPage from './pages/NetworkPortalTechniciansPage.vue'
import NetworkPortalMembershipDetailPage from './pages/NetworkPortalMembershipDetailPage.vue'
import NetworkPortalQualificationsPage from './pages/NetworkPortalQualificationsPage.vue'
import NetworkPortalQualificationDetailPage from './pages/NetworkPortalQualificationDetailPage.vue'
import NetworkPortalCapacityPage from './pages/NetworkPortalCapacityPage.vue'
import NetworkPortalCorrectionsPage from './pages/NetworkPortalCorrectionsPage.vue'
import NetworkPortalCorrectionDetailPage from './pages/NetworkPortalCorrectionDetailPage.vue'
import NetworkPortalExceptionsPage from './pages/NetworkPortalExceptionsPage.vue'
import NetworkPortalExceptionDetailPage from './pages/NetworkPortalExceptionDetailPage.vue'

export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/network-portal/workbench' },
  { path: '/network-portal/workbench', component: NetworkPortalWorkbenchPage },
  { path: '/network-portal/work-orders', component: NetworkPortalWorkOrdersPage },
  { path: '/network-portal/work-orders/:workOrderId', component: NetworkPortalWorkOrderWorkspacePage },
  { path: '/network-portal/tasks', component: NetworkPortalTasksPage },
  { path: '/network-portal/technicians', component: NetworkPortalTechniciansPage },
  { path: '/network-portal/technicians/memberships/:membershipId', component: NetworkPortalMembershipDetailPage },
  { path: '/network-portal/qualifications', component: NetworkPortalQualificationsPage },
  { path: '/network-portal/qualifications/:qualificationId', component: NetworkPortalQualificationDetailPage },
  { path: '/network-portal/capacity', component: NetworkPortalCapacityPage },
  { path: '/network-portal/corrections', component: NetworkPortalCorrectionsPage },
  { path: '/network-portal/corrections/:correctionCaseId', component: NetworkPortalCorrectionDetailPage },
  { path: '/network-portal/exceptions', component: NetworkPortalExceptionsPage },
  { path: '/network-portal/exceptions/:exceptionId', component: NetworkPortalExceptionDetailPage },
] })

export function routeForPage(pageId: string) {
  const routes: Record<string, string> = {
    'NETWORK.WORKBENCH': '/network-portal/workbench', 'NETWORK.WORKORDER.LIST': '/network-portal/work-orders',
    'NETWORK.WORKORDER.WORKSPACE': '/network-portal/work-orders', 'NETWORK.TASK.QUEUE': '/network-portal/tasks',
    'NETWORK.TECHNICIAN.LIST': '/network-portal/technicians', 'NETWORK.QUALIFICATION': '/network-portal/qualifications',
    'NETWORK.TECHNICIAN.ASSIGN': '/network-portal/tasks', 'NETWORK.APPOINTMENT': '/network-portal/tasks',
    'NETWORK.EVIDENCE.SUPPLEMENT': '/network-portal/tasks', 'NETWORK.CORRECTION.QUEUE': '/network-portal/corrections',
    'NETWORK.EXCEPTION.QUEUE': '/network-portal/exceptions', 'NETWORK.CAPACITY': '/network-portal/capacity',
  }
  return routes[pageId] ?? null
}
