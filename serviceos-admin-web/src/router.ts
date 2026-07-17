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

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/callback', name: 'ADMIN.AUTH.CALLBACK', component: OidcCallbackPage },
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
        { path: 'settings/token', name: 'ADMIN.TOKEN', component: TokenPage },
      ],
    },
  ],
})
