import { createRouter, createWebHistory } from 'vue-router'
import AppShell from './pages/AppShell.vue'
import WorkbenchPage from './pages/WorkbenchPage.vue'
import GoldenPathPage from './pages/GoldenPathPage.vue'
import DemoDataPage from './pages/DemoDataPage.vue'
import NotFoundPage from './pages/NotFoundPage.vue'
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
import FulfillmentProfileListPage from './pages/FulfillmentProfileListPage.vue'
import FulfillmentProfileCreatePage from './pages/FulfillmentProfileCreatePage.vue'
import FulfillmentProfileDetailPage from './pages/FulfillmentProfileDetailPage.vue'
import FulfillmentPreviewPage from './pages/FulfillmentPreviewPage.vue'
import FulfillmentProfileEditorPage from './pages/FulfillmentProfileEditorPage.vue'
import FulfillmentPublishFlowPage from './pages/FulfillmentPublishFlowPage.vue'
import WorkOrderFulfillmentSnapshotPage from './pages/WorkOrderFulfillmentSnapshotPage.vue'
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
import ConfigurationDesignerPage from './pages/ConfigurationDesignerPage.vue'

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
        { path: '', redirect: '/workbench' },
        {
          path: 'workbench',
          name: 'ADMIN.WORKBENCH',
          component: WorkbenchPage,
          meta: { pageId: 'ADMIN.WORKBENCH' },
        },
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
        {
          path: 'projects/:id/fulfillment-profiles',
          name: 'ADMIN.PROJECT.FULFILLMENT.LIST',
          component: FulfillmentProfileListPage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.LIST' },
        },
        {
          path: 'projects/:id/fulfillment-profiles/create',
          name: 'ADMIN.PROJECT.FULFILLMENT.CREATE',
          component: FulfillmentProfileCreatePage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.CREATE' },
        },
        {
          path: 'projects/:id/fulfillment-profiles/:profileId',
          name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
          component: FulfillmentProfileDetailPage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.DETAIL' },
        },
        {
          path: 'projects/:id/fulfillment-profiles/:profileId/preview',
          name: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW',
          component: FulfillmentPreviewPage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW' },
        },
        {
          path: 'projects/:id/fulfillment-profiles/:profileId/edit',
          name: 'ADMIN.PROJECT.FULFILLMENT.EDIT',
          component: FulfillmentProfileEditorPage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.EDIT' },
        },
        {
          path: 'projects/:id/fulfillment-profiles/:profileId/publish',
          name: 'ADMIN.PROJECT.FULFILLMENT.PUBLISH',
          component: FulfillmentPublishFlowPage,
          meta: { pageId: 'ADMIN.PROJECT.FULFILLMENT.PUBLISH' },
        },
        {
          path: 'configuration/designer',
          name: 'ADMIN.CONFIGURATION.DESIGNER',
          component: ConfigurationDesignerPage,
          meta: { pageId: 'ADMIN.CONFIGURATION.DESIGNER' },
        },
        { path: 'work-orders', name: 'ADMIN.WORKORDER.LIST', component: WorkOrderDirectoryPage },
        { path: 'work-orders/lookup', name: 'ADMIN.WORKORDER.LOOKUP', component: WorkOrderLookupPage },
        {
          path: 'work-orders/golden-path',
          name: 'ADMIN.WORKORDER.GOLDEN_PATH',
          component: GoldenPathPage,
          meta: { pageId: 'ADMIN.WORKORDER.GOLDEN_PATH' },
        },
        {
          path: 'work-orders/:id',
          name: 'ADMIN.WORKORDER.WORKSPACE',
          component: WorkOrderWorkspacePage,
        },
        {
          path: 'work-orders/:id/configuration-snapshot',
          name: 'ADMIN.WORKORDER.FULFILLMENT_SNAPSHOT',
          component: WorkOrderFulfillmentSnapshotPage,
          meta: { pageId: 'ADMIN.WORKORDER.FULFILLMENT_SNAPSHOT' },
        },
        {
          path: 'system/demo-data',
          name: 'ADMIN.SYSTEM.DEMO_DATA',
          component: DemoDataPage,
          meta: { pageId: 'ADMIN.SYSTEM.DEMO_DATA' },
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
        {
          path: ':pathMatch(.*)*',
          name: 'ADMIN.NOT_FOUND',
          component: NotFoundPage,
        },
      ],
    },
  ],
})
