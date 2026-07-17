import { createRouter, createWebHistory } from 'vue-router'
import AppShell from './pages/AppShell.vue'
import ReviewQueuePage from './pages/ReviewQueuePage.vue'
import ReviewCaseDetailPage from './pages/ReviewCaseDetailPage.vue'
import CorrectionQueuePage from './pages/CorrectionQueuePage.vue'
import CorrectionCaseDetailPage from './pages/CorrectionCaseDetailPage.vue'
import OutboundQueuePage from './pages/OutboundQueuePage.vue'
import OutboundDeliveryDetailPage from './pages/OutboundDeliveryDetailPage.vue'
import InboundEnvelopeQueuePage from './pages/InboundEnvelopeQueuePage.vue'
import InboundEnvelopeDetailPage from './pages/InboundEnvelopeDetailPage.vue'
import CanonicalMessageDetailPage from './pages/CanonicalMessageDetailPage.vue'
import ExceptionQueuePage from './pages/ExceptionQueuePage.vue'
import ExceptionDetailPage from './pages/ExceptionDetailPage.vue'
import TokenPage from './pages/TokenPage.vue'
import WorkOrderLookupPage from './pages/WorkOrderLookupPage.vue'
import WorkOrderDirectoryPage from './pages/WorkOrderDirectoryPage.vue'
import WorkOrderWorkspacePage from './pages/WorkOrderWorkspacePage.vue'
import TaskDirectoryPage from './pages/TaskDirectoryPage.vue'
import TaskDetailPage from './pages/TaskDetailPage.vue'
import SlaQueuePage from './pages/SlaQueuePage.vue'
import SlaInstanceDetailPage from './pages/SlaInstanceDetailPage.vue'
import AppointmentDetailPage from './pages/AppointmentDetailPage.vue'
import VisitDetailPage from './pages/VisitDetailPage.vue'
import ContactAttemptDetailPage from './pages/ContactAttemptDetailPage.vue'
import FormSubmissionDetailPage from './pages/FormSubmissionDetailPage.vue'
import EvidenceItemDetailPage from './pages/EvidenceItemDetailPage.vue'
import EvidenceSetSnapshotDetailPage from './pages/EvidenceSetSnapshotDetailPage.vue'
import ExternalReviewReceiptDetailPage from './pages/ExternalReviewReceiptDetailPage.vue'
import ProjectDirectoryPage from './pages/ProjectDirectoryPage.vue'
import ProjectDetailPage from './pages/ProjectDetailPage.vue'
import OidcCallbackPage from './pages/OidcCallbackPage.vue'
import UserDirectoryPage from './pages/UserDirectoryPage.vue'
import UserDetailPage from './pages/UserDetailPage.vue'
import OrganizationDirectoryPage from './pages/OrganizationDirectoryPage.vue'
import OrganizationDetailPage from './pages/OrganizationDetailPage.vue'
import NetworkDirectoryPage from './pages/NetworkDirectoryPage.vue'
import NetworkDetailPage from './pages/NetworkDetailPage.vue'
import TechnicianDirectoryPage from './pages/TechnicianDirectoryPage.vue'
import TechnicianDetailPage from './pages/TechnicianDetailPage.vue'
import RoleDirectoryPage from './pages/RoleDirectoryPage.vue'
import RoleDetailPage from './pages/RoleDetailPage.vue'
import GrantDirectoryPage from './pages/GrantDirectoryPage.vue'
import PortalStubsPage from './pages/PortalStubsPage.vue'
import UiPreferencesPage from './pages/UiPreferencesPage.vue'
import SearchPage from './pages/SearchPage.vue'
import NetworkPortalShell from './pages/NetworkPortalShell.vue'
import NetworkPortalWorkbenchPage from './pages/NetworkPortalWorkbenchPage.vue'
import NetworkPortalWorkOrdersPage from './pages/NetworkPortalWorkOrdersPage.vue'
import NetworkPortalTasksPage from './pages/NetworkPortalTasksPage.vue'
import NetworkPortalTechniciansPage from './pages/NetworkPortalTechniciansPage.vue'
import NetworkPortalCorrectionsPage from './pages/NetworkPortalCorrectionsPage.vue'
import TechnicianPortalShell from './pages/TechnicianPortalShell.vue'
import TechnicianPortalTaskFeedPage from './pages/TechnicianPortalTaskFeedPage.vue'
import TechnicianPortalSchedulePage from './pages/TechnicianPortalSchedulePage.vue'
import TechnicianPortalSyncSummaryPage from './pages/TechnicianPortalSyncSummaryPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/callback', name: 'ADMIN.AUTH.CALLBACK', component: OidcCallbackPage },
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: 'search',
          name: 'ADMIN.SEARCH',
          component: SearchPage,
          meta: { pageId: 'ADMIN.SEARCH' },
        },
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
        {
          path: 'integration/inbound',
          name: 'ADMIN.INTEGRATION.INBOUND',
          component: InboundEnvelopeQueuePage,
        },
        {
          path: 'integration/inbound/:id',
          name: 'ADMIN.INTEGRATION.INBOUND.DETAIL',
          component: InboundEnvelopeDetailPage,
        },
        {
          path: 'integration/canonical/:id',
          name: 'ADMIN.INTEGRATION.CANONICAL.DETAIL',
          component: CanonicalMessageDetailPage,
        },
        { path: 'exceptions', name: 'ADMIN.EXCEPTION.QUEUE', component: ExceptionQueuePage },
        { path: 'exceptions/:id', name: 'ADMIN.EXCEPTION.DETAIL', component: ExceptionDetailPage },
        { path: 'tasks', name: 'ADMIN.TASK.QUEUE', component: TaskDirectoryPage },
        { path: 'tasks/:id', name: 'ADMIN.TASK.DETAIL', component: TaskDetailPage },
        {
          path: 'appointments/:id',
          name: 'ADMIN.APPOINTMENT.DETAIL',
          component: AppointmentDetailPage,
        },
        {
          path: 'visits/:id',
          name: 'ADMIN.VISIT.DETAIL',
          component: VisitDetailPage,
        },
        {
          path: 'contact-attempts/:id',
          name: 'ADMIN.CONTACT_ATTEMPT.DETAIL',
          component: ContactAttemptDetailPage,
        },
        {
          path: 'form-submissions/:id',
          name: 'ADMIN.FORM_SUBMISSION.DETAIL',
          component: FormSubmissionDetailPage,
        },
        {
          path: 'evidence-items/:id',
          name: 'ADMIN.EVIDENCE_ITEM.DETAIL',
          component: EvidenceItemDetailPage,
        },
        {
          path: 'evidence-set-snapshots/:id',
          name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
          component: EvidenceSetSnapshotDetailPage,
        },
        {
          path: 'external-review-receipts/:id',
          name: 'ADMIN.EXTERNAL_REVIEW_RECEIPT.DETAIL',
          component: ExternalReviewReceiptDetailPage,
        },
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
        // M187 Admin 统一用户中心：稳定 pageId = route name
        { path: 'users', name: 'ADMIN.USER.DIRECTORY', component: UserDirectoryPage, meta: { pageId: 'ADMIN.USER.DIRECTORY' } },
        { path: 'users/:id', name: 'ADMIN.USER.DETAIL', component: UserDetailPage, meta: { pageId: 'ADMIN.USER.DETAIL' } },
        {
          path: 'organizations',
          name: 'ADMIN.ORGANIZATION.DIRECTORY',
          component: OrganizationDirectoryPage,
          meta: { pageId: 'ADMIN.ORGANIZATION.DIRECTORY' },
        },
        {
          path: 'organizations/:id',
          name: 'ADMIN.ORGANIZATION.DETAIL',
          component: OrganizationDetailPage,
          meta: { pageId: 'ADMIN.ORGANIZATION.DETAIL' },
        },
        {
          path: 'networks',
          name: 'ADMIN.NETWORK.DIRECTORY',
          component: NetworkDirectoryPage,
          meta: { pageId: 'ADMIN.NETWORK.DIRECTORY' },
        },
        {
          path: 'networks/:id',
          name: 'ADMIN.NETWORK.DETAIL',
          component: NetworkDetailPage,
          meta: { pageId: 'ADMIN.NETWORK.DETAIL' },
        },
        {
          path: 'technicians',
          name: 'ADMIN.TECHNICIAN.DIRECTORY',
          component: TechnicianDirectoryPage,
          meta: { pageId: 'ADMIN.TECHNICIAN.DIRECTORY' },
        },
        {
          path: 'technicians/:id',
          name: 'ADMIN.TECHNICIAN.DETAIL',
          component: TechnicianDetailPage,
          meta: { pageId: 'ADMIN.TECHNICIAN.DETAIL' },
        },
        {
          path: 'roles',
          name: 'ADMIN.ROLE.DIRECTORY',
          component: RoleDirectoryPage,
          meta: { pageId: 'ADMIN.ROLE.DIRECTORY' },
        },
        {
          path: 'roles/:id',
          name: 'ADMIN.ROLE.DETAIL',
          component: RoleDetailPage,
          meta: { pageId: 'ADMIN.ROLE.DETAIL' },
        },
        {
          path: 'grants',
          name: 'ADMIN.GRANT.DIRECTORY',
          component: GrantDirectoryPage,
          meta: { pageId: 'ADMIN.GRANT.DIRECTORY' },
        },
        {
          path: 'portal-stubs',
          name: 'ADMIN.PORTAL.STUBS',
          component: PortalStubsPage,
          meta: { pageId: 'ADMIN.PORTAL.STUBS' },
        },
        { path: 'settings/preferences', name: 'ADMIN.UI.PREFERENCES', component: UiPreferencesPage },
        { path: 'settings/token', name: 'ADMIN.TOKEN', component: TokenPage },
      ],
    },
    {
      path: '/network-portal',
      component: NetworkPortalShell,
      children: [
        { path: '', redirect: { name: 'NETWORK.WORKBENCH' } },
        {
          path: 'workbench',
          name: 'NETWORK.WORKBENCH',
          component: NetworkPortalWorkbenchPage,
          meta: { pageId: 'NETWORK.WORKBENCH' },
        },
        {
          path: 'work-orders',
          name: 'NETWORK.WORKORDER.LIST',
          component: NetworkPortalWorkOrdersPage,
          meta: { pageId: 'NETWORK.WORKORDER.LIST' },
        },
        {
          path: 'tasks',
          name: 'NETWORK.TASK.QUEUE',
          component: NetworkPortalTasksPage,
          meta: { pageId: 'NETWORK.TASK.QUEUE' },
        },
        {
          path: 'technicians',
          name: 'NETWORK.TECHNICIAN.LIST',
          component: NetworkPortalTechniciansPage,
          meta: { pageId: 'NETWORK.TECHNICIAN.LIST' },
        },
        {
          path: 'corrections',
          name: 'NETWORK.CORRECTION.QUEUE',
          component: NetworkPortalCorrectionsPage,
          meta: { pageId: 'NETWORK.CORRECTION.QUEUE' },
        },
      ],
    },
    {
      path: '/technician-portal',
      component: TechnicianPortalShell,
      children: [
        { path: '', redirect: { name: 'TECHNICIAN.TASK.LIST' } },
        {
          path: 'task-feed',
          name: 'TECHNICIAN.TASK.LIST',
          component: TechnicianPortalTaskFeedPage,
          meta: { pageId: 'TECHNICIAN.TASK.LIST' },
        },
        {
          path: 'schedule',
          name: 'TECHNICIAN.SCHEDULE',
          component: TechnicianPortalSchedulePage,
          meta: { pageId: 'TECHNICIAN.SCHEDULE' },
        },
        {
          path: 'sync-summary',
          name: 'TECHNICIAN.SYNC.SUMMARY',
          component: TechnicianPortalSyncSummaryPage,
          meta: { pageId: 'TECHNICIAN.SYNC.SUMMARY' },
        },
      ],
    },
  ],
})
