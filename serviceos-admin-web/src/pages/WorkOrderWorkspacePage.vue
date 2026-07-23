<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Button, Tabs, TabPane, Alert, Select, Descriptions, Space, Card, Drawer } from 'ant-design-vue'
import {
  ToolOutlined,
} from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import BusinessProgress, { type BusinessProgressStep } from '../patterns/BusinessProgress.vue'
import AllowedActionBar, { type AllowedActionItem } from '../patterns/AllowedActionBar.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import SensitiveText from '../components/business/SensitiveText.vue'
import { presentWorkOrderStatus } from '../presentation/work-order-status.presenter'
import { presentPricingStatus } from '../presentation/pricing-status.presenter'
import { presentReviewStatus } from '../presentation/review-status.presenter'
import { presentCorrectionStatus } from '../presentation/correction-status.presenter'
import { labelClientCode, labelServiceProduct } from '../presentation/enum-labels'
import { presentEntityName } from '../presentation/entity-name.presenter'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { presentEmptyValue } from '../presentation/empty-value.presenter'
import { labelAction } from '../presentation/action-labels'
import { presentWorkOrderTimelineEvent } from '../presentation/work-order-timeline.presenter'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'
import { getAuthorizedProject } from '../api/projectDetail'
import { listServiceNetworks, type ServiceNetwork } from '../api/networks'
import { listTechnicianProfiles, type TechnicianProfile } from '../api/technicians'
import {
  getWorkOrderActivitySummary,
  getWorkOrderWorkspace,
  getWorkOrderWorkspaceSection,
  type SectionCode,
  type WorkOrderActivitySummary,
  type WorkOrderWorkspace,
  type WorkOrderWorkspaceSection,
} from '../api/workspace'
import { listWorkOrderSlaInstances, type SlaInstancePage } from '../api/sla'
import {
  getAuthorizedWorkOrder,
  getAuthorizedWorkOrderStages,
  listAuthorizedWorkOrderTasks,
  listWorkOrderCoreTimeline,
  listWorkOrderPricingSnapshots,
  type PricingShadowSnapshotPage,
  type WorkOrderDetail,
  type WorkOrderTaskPage,
  type WorkOrderTimelinePage,
  type WorkflowExecutionProjection,
} from '../api/workOrderDetail'
import { getTaskAllowedActions, type TaskAllowedActions } from '../api/tasks'
import { authorizeEvidenceRevisionDownload } from '../api/finalReview'
import {
  getWorkOrderFulfillmentSnapshot,
  type WorkOrderFulfillmentSnapshot,
} from '../api/fulfillmentProfiles'
import {
  getNetworkAssignmentCandidates,
  manualAssignNetworkServiceAssignment,
  type NetworkAssignmentCandidateView,
} from '../api/dispatch'
import FinalReviewWorkspace from '../features/work-orders/components/final-review/FinalReviewWorkspace.vue'
import { recordRecentVisit } from '../recent/recordRecentVisit'
import { statusLabel } from '../product/statusLabels'

const route = useRoute()
const router = useRouter()
const diagnostics = useDeveloperDiagnostics()
const workOrderId = computed(() => String(route.params.id ?? ''))
const productTab = ref('overview')
const fulfillmentSnapshot = ref<WorkOrderFulfillmentSnapshot | null>(null)
const projectName = ref<string | null>(null)
const networkDirectory = ref<ServiceNetwork[]>([])
const technicianDirectory = ref<TechnicianProfile[]>([])
const directoryLoaded = ref(false)
const assignNetworkDrawerOpen = ref(false)

function onProductTabChange(key: string | number) {
  const k = String(key)
  productTab.value = k
  if (k === 'TASKS' || k === 'FORMS_EVIDENCE' || k === 'REVIEWS_CORRECTIONS' || k === 'FINAL_REVIEW' || k === 'TIMELINE_AUDIT' || k === 'INTEGRATION' || k === 'APPOINTMENTS_VISITS') {
    void loadSection(k as SectionCode)
  }
}

const loading = ref(false)
const error = ref<string | null>(null)
const workspace = ref<WorkOrderWorkspace | null>(null)
const activity = ref<WorkOrderActivitySummary | null>(null)
const allowedActions = ref<TaskAllowedActions | null>(null)
const allowedActionsError = ref<string | null>(null)
const activeSection = ref<SectionCode>('TASKS')
const sectionLoading = ref(false)
const sectionError = ref<string | null>(null)
const sectionData = ref<WorkOrderWorkspaceSection | null>(null)
const slaPage = ref<SlaInstancePage | null>(null)
const slaError = ref<string | null>(null)
const workOrderDetail = ref<WorkOrderDetail | null>(null)
const stages = ref<WorkflowExecutionProjection | null>(null)
const taskPage = ref<WorkOrderTaskPage | null>(null)
const timelinePage = ref<WorkOrderTimelinePage | null>(null)
const authorityError = ref<string | null>(null)
const networkAssigneeId = ref<string>()
const networkCandidates = ref<NetworkAssignmentCandidateView | null>(null)
const networkCandidatesLoading = ref(false)
const networkCandidatesError = ref<string | null>(null)
const assignNetworkBusy = ref(false)
const assignNetworkError = ref<string | null>(null)
const assignNetworkMessage = ref<string | null>(null)
const pricingSnapshots = ref<PricingShadowSnapshotPage | null>(null)
const pricingSnapshotsError = ref<string | null>(null)

async function runAssignNetwork() {
  const taskId = workspace.value?.currentTaskSummary?.taskId
  if (!taskId) {
    assignNetworkError.value = '无当前任务，无法派给网点'
    return
  }
  assignNetworkBusy.value = true
  assignNetworkError.value = null
  assignNetworkMessage.value = null
  try {
    const candidate = networkCandidates.value?.candidates.find(
      (item) => item.networkId === networkAssigneeId.value,
    )
    if (!candidate || !networkCandidates.value) {
      assignNetworkError.value = '请先选择当前可分配的责任网点'
      return
    }
    await manualAssignNetworkServiceAssignment(taskId, {
      networkAssigneeId: candidate.networkId,
      businessType: networkCandidates.value.businessType,
    })
    assignNetworkMessage.value =
      `责任网点已分配给“${candidate.networkName}”，网点端可继续接单并安排师傅。`
    await loadWorkspace()
    assignNetworkDrawerOpen.value = false
  } catch (err) {
    assignNetworkError.value = err instanceof Error ? err.message : '派网点失败'
  } finally {
    assignNetworkBusy.value = false
  }
}

async function loadNetworkCandidates(taskId: string | undefined) {
  networkAssigneeId.value = undefined
  networkCandidates.value = null
  networkCandidatesError.value = null
  if (!taskId) return
  networkCandidatesLoading.value = true
  try {
    networkCandidates.value = await getNetworkAssignmentCandidates(taskId)
  } catch (err) {
    networkCandidatesError.value = err instanceof Error ? err.message : '加载责任网点候选失败'
  } finally {
    networkCandidatesLoading.value = false
  }
}

const sections: SectionCode[] = [
  'TASKS',
  'TIMELINE_AUDIT',
  'APPOINTMENTS_VISITS',
  'FORMS_EVIDENCE',
  'REVIEWS_CORRECTIONS',
  'FINAL_REVIEW',
  'INTEGRATION',
]

const sectionLabels: Record<SectionCode, string> = {
  TASKS: '任务',
  TIMELINE_AUDIT: '时间线',
  APPOINTMENTS_VISITS: '预约到场',
  FORMS_EVIDENCE: '表单资料',
  REVIEWS_CORRECTIONS: '审核整改',
  FINAL_REVIEW: '平台终审',
  INTEGRATION: '集成',
}

async function loadAllowedActions(taskId: string | undefined) {
  allowedActions.value = null
  allowedActionsError.value = null
  if (!taskId) {
    return
  }
  try {
    const result = await getTaskAllowedActions(taskId)
    allowedActions.value = result.data
  } catch (err) {
    allowedActionsError.value = err instanceof Error ? err.message : '加载 allowed-actions 失败'
  }
}

async function loadSlaInstances() {
  slaError.value = null
  try {
    slaPage.value = await listWorkOrderSlaInstances(workOrderId.value, { limit: '20' })
  } catch (err) {
    slaError.value = err instanceof Error ? err.message : '加载工单 SLA 失败'
    slaPage.value = null
  }
}

async function loadAuthorityProjections() {
  authorityError.value = null
  try {
    const [detail, stageProjection, tasks, timeline] = await Promise.all([
      getAuthorizedWorkOrder(workOrderId.value),
      getAuthorizedWorkOrderStages(workOrderId.value),
      listAuthorizedWorkOrderTasks(workOrderId.value, { limit: '20' }),
      listWorkOrderCoreTimeline(workOrderId.value, { limit: '20' }),
    ])
    workOrderDetail.value = detail
    stages.value = stageProjection
    taskPage.value = tasks
    timelinePage.value = timeline
  } catch (err) {
    authorityError.value = err instanceof Error ? err.message : '加载工单权威投影失败'
    workOrderDetail.value = null
    stages.value = null
    taskPage.value = null
    timelinePage.value = null
  }
}

async function loadPricingSnapshots() {
  pricingSnapshotsError.value = null
  try {
    const result = await listWorkOrderPricingSnapshots(workOrderId.value)
    pricingSnapshots.value = result
  } catch (err) {
    pricingSnapshotsError.value = err instanceof Error ? err.message : '加载影子试算失败'
    pricingSnapshots.value = null
  }
}

async function loadFulfillmentSnapshot() {
  try {
    fulfillmentSnapshot.value = await getWorkOrderFulfillmentSnapshot(workOrderId.value)
  } catch {
    fulfillmentSnapshot.value = null
  }
}

async function loadProductNames(projectId: string) {
  directoryLoaded.value = false
  projectName.value = null
  networkDirectory.value = []
  technicianDirectory.value = []
  const [projectResult, networkResult, technicianResult] = await Promise.allSettled([
    getAuthorizedProject(projectId),
    listServiceNetworks(),
    listTechnicianProfiles(),
  ])
  if (projectResult.status === 'fulfilled') {
    projectName.value = projectResult.value.project.name
  }
  if (networkResult.status === 'fulfilled') {
    networkDirectory.value = networkResult.value.items
  }
  if (technicianResult.status === 'fulfilled') {
    technicianDirectory.value = technicianResult.value.items
  }
  directoryLoaded.value = true
}

async function loadWorkspace() {
  loading.value = true
  error.value = null
  try {
    const [ws, act] = await Promise.all([
      getWorkOrderWorkspace(workOrderId.value),
      getWorkOrderActivitySummary(workOrderId.value),
    ])
    workspace.value = ws
    activity.value = act
    await Promise.all([
      loadProductNames(ws.header.projectId),
      loadAllowedActions(ws.currentTaskSummary?.taskId),
      loadNetworkCandidates(ws.currentTaskSummary?.taskId),
      loadSlaInstances(),
      loadAuthorityProjections(),
      loadPricingSnapshots(),
      loadFulfillmentSnapshot(),
    ])
    const requestedTab = String(route.query.tab ?? '')
    const tabSection = sections.find((code) => code === requestedTab)
    const firstAvailable = sections.find(
      (code) => ws.sectionAvailability[code] === 'AVAILABLE' || ws.sectionAvailability[code] === 'EMPTY',
    )
    const defaultProductSection =
      ws.sectionAvailability.FORMS_EVIDENCE === 'AVAILABLE' ||
      ws.sectionAvailability.FORMS_EVIDENCE === 'EMPTY'
        ? 'FORMS_EVIDENCE'
        : firstAvailable
    activeSection.value = tabSection ?? defaultProductSection ?? 'TASKS'
    // 高保真基线以现场资料作为默认业务视图；显式深链仍优先于默认页签。
    productTab.value = tabSection ?? defaultProductSection ?? 'overview'
    await loadSection(activeSection.value)
    diagnostics.pushDiagnostic({
      title: '工单工作区技术上下文',
      fields: {
        workOrderId: workOrderId.value,
        allowedActionLink: ws.allowedActionLink,
        asOf: ws.meta.asOf,
        timelineFreshnessStatus: ws.timelineFreshnessStatus,
        resourceVersion: allowedActions.value?.resourceVersion,
      },
    })
    recordRecentVisit({
      resourceType: 'WORK_ORDER',
      resourceId: workOrderId.value,
      pageId: 'ADMIN.WORKORDER.WORKSPACE',
      displayRef:
        workOrderDetail.value?.workOrder?.externalOrderCode ?? workOrderId.value.slice(0, 8),
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载工作区失败'
    workspace.value = null
    activity.value = null
    allowedActions.value = null
    slaPage.value = null
    workOrderDetail.value = null
    stages.value = null
    taskPage.value = null
    timelinePage.value = null
    pricingSnapshots.value = null
  } finally {
    loading.value = false
  }
}

async function loadSection(section: SectionCode) {
  activeSection.value = section
  if (section === 'FINAL_REVIEW') {
    sectionLoading.value = false
    sectionError.value = null
    sectionData.value = null
    return
  }
  sectionLoading.value = true
  sectionError.value = null
  try {
    sectionData.value = await getWorkOrderWorkspaceSection(workOrderId.value, section, { limit: '20' })
  } catch (err) {
    sectionError.value = err instanceof Error ? err.message : '加载区块失败'
    sectionData.value = null
  } finally {
    sectionLoading.value = false
  }
}

// 区块中文名称供产品化关联入口复用。
void sectionLabels

type InboundEnvelopeLink = {
  inboundEnvelopeId: string
  messageType: string
  processingStatus: string
  resultCode: string | null
  canonicalMessageId: string
}

type CanonicalMessageLink = {
  canonicalMessageId: string
  messageType: string
  processingStatus: string
}

type OutboundDeliveryLink = {
  deliveryId: string
  businessMessageType: string
  status: string
  externalOrderCode: string
  sourceReviewCaseId: string
  sourceTaskId: string
  sourceSnapshotId: string
  clientReviewCaseId: string
}

type OutboundCrossLink = {
  key: string
  routeName: string
  resourceId: string
  label: string
}

type ReviewDecisionRow = {
  reviewDecisionId: string
  decisionOrdinal: number
  decision: string
  decisionSource: string
  reasonCodes: string[]
  decidedAt: string
}

type ReviewCaseLink = {
  reviewCaseId: string
  origin: string
  status: string
  evidenceSetSnapshotId: string
  reopenedFromReviewCaseId: string
  /** M425：工作区已投影的完整决策记录（无 note/decidedBy）。 */
  decisions: ReviewDecisionRow[]
}

type CorrectionResubmissionRow = {
  correctionResubmissionId: string
  resubmissionOrdinal: number
  evidenceSetSnapshotId: string
  submittedAt: string
}

type CorrectionCaseLink = {
  correctionCaseId: string
  status: string
  sourceReviewCaseId: string
  latestResubmissionSnapshotId: string
  /** M425：补传轮次摘要（与决策记录同屏产品化）。 */
  resubmissions: CorrectionResubmissionRow[]
}

type ReviewCorrectionCrossLink = {
  key: string
  routeName: string
  resourceId: string
  label: string
}

type TaskSectionLink = {
  taskId: string
  taskType: string
  taskKind: string
  status: string
}

type TimelineResourceLink = {
  key: string
  routeName: string
  resourceId: string
  eventType: string
  resourceType: string
  label: string
}

type RelatedTaskLink = {
  key: string
  taskId: string
  label: string
}

type AppointmentDetailLink = {
  appointmentId: string
  type: string
  status: string
}

type VisitDetailLink = {
  visitId: string
  status: string
  visitSequence: string
}

type ContactAttemptDetailLink = {
  contactAttemptId: string
  channel: string
  resultCode: string
}

type FormSubmissionDetailLink = {
  submissionId: string
  formKey: string
  validationStatus: string
}

type EvidenceItemDetailLink = {
  evidenceItemId: string
  status: string
  itemOrdinal: string
  /** M426：最新 revision 指针；无修订时为 null。 */
  latestRevisionId: string | null
  latestMimeType: string | null
}

/** 仅映射已有 Admin 详情路由；无对等页的 resourceType 不渲染。 */
const TIMELINE_RESOURCE_ROUTES: Record<string, string> = {
  WorkOrder: 'ADMIN.WORKORDER.WORKSPACE',
  Task: 'ADMIN.TASK.DETAIL',
  Appointment: 'ADMIN.APPOINTMENT.DETAIL',
  Visit: 'ADMIN.VISIT.DETAIL',
  ContactAttempt: 'ADMIN.CONTACT_ATTEMPT.DETAIL',
  FormSubmission: 'ADMIN.FORM_SUBMISSION.DETAIL',
  EvidenceItem: 'ADMIN.EVIDENCE_ITEM.DETAIL',
  EvidenceSetSnapshot: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
  ExternalReviewReceipt: 'ADMIN.EXTERNAL_REVIEW_RECEIPT.DETAIL',
  ReviewCase: 'ADMIN.REVIEW.DETAIL',
  CorrectionCase: 'ADMIN.CORRECTION.DETAIL',
  OutboundDelivery: 'ADMIN.INTEGRATION.DETAIL',
  OperationalException: 'ADMIN.EXCEPTION.DETAIL',
  SlaInstance: 'ADMIN.SLA.DETAIL',
}

function collectRelatedTaskLinks(
  rows: unknown,
  toLabel: (row: Record<string, unknown>, taskId: string) => string | null,
): RelatedTaskLink[] {
  if (!Array.isArray(rows)) return []
  const seen = new Set<string>()
  const links: RelatedTaskLink[] = []
  for (const item of rows) {
    if (!item || typeof item !== 'object') continue
    const row = item as Record<string, unknown>
    const taskId = typeof row.taskId === 'string' ? row.taskId : ''
    if (!taskId || seen.has(taskId)) continue
    const label = toLabel(row, taskId)
    if (!label) continue
    seen.add(taskId)
    links.push({ key: taskId, taskId, label })
  }
  return links
}

const inboundEnvelopeLinks = computed((): InboundEnvelopeLink[] => {
  const integration = sectionData.value?.integration
  if (!integration || activeSection.value !== 'INTEGRATION') return []
  const raw = integration.inboundEnvelopes
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.inboundEnvelopeId
      if (typeof id !== 'string' || !id) return null
      const canonicalMessageId =
        typeof row.canonicalMessageId === 'string' ? row.canonicalMessageId : ''
      return {
        inboundEnvelopeId: id,
        messageType: String(row.messageType ?? '—'),
        processingStatus: String(row.processingStatus ?? '—'),
        resultCode: row.resultCode == null ? null : String(row.resultCode),
        canonicalMessageId,
      }
    })
    .filter((item): item is InboundEnvelopeLink => item != null)
})

/** 复用已 Implemented Canonical GET；仅渲染投影已给出的 canonicalMessageId。 */
const canonicalMessageLinks = computed((): CanonicalMessageLink[] => {
  if (activeSection.value !== 'INTEGRATION') return []
  const seen = new Set<string>()
  const links: CanonicalMessageLink[] = []
  for (const item of inboundEnvelopeLinks.value) {
    if (!item.canonicalMessageId || seen.has(item.canonicalMessageId)) continue
    seen.add(item.canonicalMessageId)
    links.push({
      canonicalMessageId: item.canonicalMessageId,
      messageType: item.messageType,
      processingStatus: item.processingStatus,
    })
  }
  return links
})

/** 复用已 Implemented Outbound 详情路由；投影缺权时 outboundDeliveries 为 null。 */
const outboundDeliveryLinks = computed((): OutboundDeliveryLink[] => {
  const integration = sectionData.value?.integration
  if (!integration || activeSection.value !== 'INTEGRATION') return []
  const raw = integration.outboundDeliveries
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.deliveryId
      const sourceReviewCaseId = row.sourceReviewCaseId
      const sourceTaskId = row.sourceTaskId
      const sourceSnapshotId = row.sourceSnapshotId
      if (typeof id !== 'string' || !id) return null
      if (typeof sourceReviewCaseId !== 'string' || !sourceReviewCaseId) return null
      if (typeof sourceTaskId !== 'string' || !sourceTaskId) return null
      if (typeof sourceSnapshotId !== 'string' || !sourceSnapshotId) return null
      return {
        deliveryId: id,
        businessMessageType: String(row.businessMessageType ?? '—'),
        status: String(row.status ?? '—'),
        externalOrderCode: String(row.externalOrderCode ?? id),
        sourceReviewCaseId,
        sourceTaskId,
        sourceSnapshotId,
        clientReviewCaseId:
          typeof row.clientReviewCaseId === 'string' ? row.clientReviewCaseId : '',
      }
    })
    .filter((item): item is OutboundDeliveryLink => item != null)
})

/**
 * 工作区外发关联资源深链：仅使用 Accepted INTEGRATION 投影字段。
 * 前缀 ob / 避免与交付详情链接及详情页交叉链严格模式冲突。
 */
const outboundCrossLinks = computed((): OutboundCrossLink[] => {
  if (activeSection.value !== 'INTEGRATION') return []
  const links: OutboundCrossLink[] = []
  const seen = new Set<string>()
  for (const item of outboundDeliveryLinks.value) {
    const reviewKey = `source-review:${item.sourceReviewCaseId}`
    if (!seen.has(reviewKey)) {
      seen.add(reviewKey)
      links.push({
        key: reviewKey,
        routeName: 'ADMIN.REVIEW.DETAIL',
        resourceId: item.sourceReviewCaseId,
        label: '查看来源审核记录',
      })
    }
    const taskKey = `source-task:${item.sourceTaskId}`
    if (!seen.has(taskKey)) {
      seen.add(taskKey)
      links.push({
        key: taskKey,
        routeName: 'ADMIN.TASK.DETAIL',
        resourceId: item.sourceTaskId,
        label: '查看来源任务',
      })
    }
    const snapshotKey = `source-snapshot:${item.sourceSnapshotId}`
    if (!seen.has(snapshotKey)) {
      seen.add(snapshotKey)
      links.push({
        key: snapshotKey,
        routeName: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
        resourceId: item.sourceSnapshotId,
        label: '查看来源资料快照',
      })
    }
    if (item.clientReviewCaseId) {
      const clientKey = `client-review:${item.clientReviewCaseId}`
      if (!seen.has(clientKey)) {
        seen.add(clientKey)
        links.push({
          key: clientKey,
          routeName: 'ADMIN.REVIEW.DETAIL',
          resourceId: item.clientReviewCaseId,
          label: '查看客户审核记录',
        })
      }
    }
  }
  return links
})

function parseReviewDecisions(raw: unknown): ReviewDecisionRow[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.reviewDecisionId
      if (typeof id !== 'string' || !id) return null
      const ordinal = Number(row.decisionOrdinal)
      if (!Number.isFinite(ordinal) || ordinal < 1) return null
      const decidedAt = typeof row.decidedAt === 'string' ? row.decidedAt : ''
      if (!decidedAt) return null
      const reasonCodes = Array.isArray(row.reasonCodes)
        ? row.reasonCodes.filter((code): code is string => typeof code === 'string' && code.length > 0)
        : []
      return {
        reviewDecisionId: id,
        decisionOrdinal: ordinal,
        decision: String(row.decision ?? ''),
        decisionSource: String(row.decisionSource ?? ''),
        reasonCodes,
        decidedAt,
      }
    })
    .filter((item): item is ReviewDecisionRow => item != null)
    .sort((a, b) => a.decisionOrdinal - b.decisionOrdinal)
}

function parseCorrectionResubmissions(raw: unknown): CorrectionResubmissionRow[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.correctionResubmissionId
      const snapshotId = row.evidenceSetSnapshotId
      if (typeof id !== 'string' || !id) return null
      if (typeof snapshotId !== 'string' || !snapshotId) return null
      const ordinal = Number(row.resubmissionOrdinal)
      if (!Number.isFinite(ordinal) || ordinal < 1) return null
      const submittedAt = typeof row.submittedAt === 'string' ? row.submittedAt : ''
      if (!submittedAt) return null
      return {
        correctionResubmissionId: id,
        resubmissionOrdinal: ordinal,
        evidenceSetSnapshotId: snapshotId,
        submittedAt,
      }
    })
    .filter((item): item is CorrectionResubmissionRow => item != null)
    .sort((a, b) => a.resubmissionOrdinal - b.resubmissionOrdinal)
}

/** 复用已 Implemented Review/Correction 详情路由；投影缺权时数组为 null。 */
const reviewCaseLinks = computed((): ReviewCaseLink[] => {
  const section = sectionData.value?.reviewsCorrections
  if (!section || activeSection.value !== 'REVIEWS_CORRECTIONS') return []
  const raw = (section as Record<string, unknown>).reviews
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.reviewCaseId
      const snapshotId = row.evidenceSetSnapshotId
      if (typeof id !== 'string' || !id) return null
      if (typeof snapshotId !== 'string' || !snapshotId) return null
      const reopenedFrom =
        typeof row.reopenedFromReviewCaseId === 'string' ? row.reopenedFromReviewCaseId : ''
      return {
        reviewCaseId: id,
        origin: String(row.origin ?? '—'),
        status: String(row.status ?? '—'),
        evidenceSetSnapshotId: snapshotId,
        reopenedFromReviewCaseId: reopenedFrom,
        decisions: parseReviewDecisions(row.decisions),
      }
    })
    .filter((item): item is ReviewCaseLink => item != null)
})

const correctionCaseLinks = computed((): CorrectionCaseLink[] => {
  const section = sectionData.value?.reviewsCorrections
  if (!section || activeSection.value !== 'REVIEWS_CORRECTIONS') return []
  const raw = (section as Record<string, unknown>).corrections
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.correctionCaseId
      const sourceReviewCaseId = row.sourceReviewCaseId
      if (typeof id !== 'string' || !id) return null
      if (typeof sourceReviewCaseId !== 'string' || !sourceReviewCaseId) return null
      const latestSnapshot =
        typeof row.latestResubmissionSnapshotId === 'string'
          ? row.latestResubmissionSnapshotId
          : ''
      return {
        correctionCaseId: id,
        status: String(row.status ?? '—'),
        sourceReviewCaseId,
        latestResubmissionSnapshotId: latestSnapshot,
        resubmissions: parseCorrectionResubmissions(row.resubmissions),
      }
    })
    .filter((item): item is CorrectionCaseLink => item != null)
})

/**
 * 工作区区块级交叉深链：仅使用 Accepted 投影字段，目标复用已有详情页。
 * 前缀 rc / 避免与详情页「打开资料快照」及时间线资源链接严格模式冲突。
 */
const reviewCorrectionCrossLinks = computed((): ReviewCorrectionCrossLink[] => {
  if (activeSection.value !== 'REVIEWS_CORRECTIONS') return []
  const links: ReviewCorrectionCrossLink[] = []
  const seen = new Set<string>()
  for (const review of reviewCaseLinks.value) {
    const snapshotKey = `snapshot:${review.evidenceSetSnapshotId}`
    if (!seen.has(snapshotKey)) {
      seen.add(snapshotKey)
      links.push({
        key: snapshotKey,
        routeName: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
        resourceId: review.evidenceSetSnapshotId,
        label: '查看审核资料快照',
      })
    }
    if (review.reopenedFromReviewCaseId) {
      const sourceKey = `source-review:${review.reopenedFromReviewCaseId}`
      if (!seen.has(sourceKey)) {
        seen.add(sourceKey)
        links.push({
          key: sourceKey,
          routeName: 'ADMIN.REVIEW.DETAIL',
          resourceId: review.reopenedFromReviewCaseId,
          label: '查看重开前的审核记录',
        })
      }
    }
  }
  for (const correction of correctionCaseLinks.value) {
    const sourceKey = `correction-source-review:${correction.sourceReviewCaseId}`
    if (!seen.has(sourceKey)) {
      seen.add(sourceKey)
      links.push({
        key: sourceKey,
        routeName: 'ADMIN.REVIEW.DETAIL',
        resourceId: correction.sourceReviewCaseId,
        label: '查看整改来源审核',
      })
    }
    if (correction.latestResubmissionSnapshotId) {
      const resubmitKey = `resubmit-snapshot:${correction.latestResubmissionSnapshotId}`
      if (!seen.has(resubmitKey)) {
        seen.add(resubmitKey)
        links.push({
          key: resubmitKey,
          routeName: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
          resourceId: correction.latestResubmissionSnapshotId,
          label: '查看最近补充资料',
        })
      }
    }
  }
  return links
})

/** 复用已 Implemented Task 详情路由；与权威 Task 表深链并列，覆盖按需 TASKS 区块。 */
const taskSectionLinks = computed((): TaskSectionLink[] => {
  const section = sectionData.value?.tasks
  if (!section || activeSection.value !== 'TASKS') return []
  const raw = section.items
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.taskId
      if (typeof id !== 'string' || !id) return null
      return {
        taskId: id,
        taskType: String(row.taskType ?? '—'),
        taskKind: String(row.taskKind ?? '—'),
        status: String(row.status ?? '—'),
      }
    })
    .filter((item): item is TaskSectionLink => item != null)
})

function collectTimelineResourceLinks(
  rows: unknown,
  labelPrefix: string | null,
): TimelineResourceLink[] {
  if (!Array.isArray(rows)) return []
  const seen = new Set<string>()
  return rows
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const resourceType = typeof row.resourceType === 'string' ? row.resourceType : ''
      const resourceId = typeof row.resourceId === 'string' ? row.resourceId : ''
      const routeName = TIMELINE_RESOURCE_ROUTES[resourceType]
      if (!routeName || !resourceId) return null
      const dedupeKey = `${labelPrefix ?? 'audit'}:${resourceType}:${resourceId}`
      if (seen.has(dedupeKey)) return null
      seen.add(dedupeKey)
      const eventType = String(row.eventType ?? '—')
      const eventLabel = presentWorkOrderTimelineEvent(eventType).label
      const body = `${eventLabel} · 查看关联业务记录`
      return {
        key: dedupeKey,
        routeName,
        resourceId,
        eventType,
        resourceType,
        label: labelPrefix ? `${body}（${labelPrefix === 'core' ? '完整时间线' : '最近动态'}）` : body,
      }
    })
    .filter((item): item is TimelineResourceLink => item != null)
}

/** 按需 TIMELINE_AUDIT：仅对已有详情页的 resourceType 生成深链。 */
const timelineResourceLinks = computed((): TimelineResourceLink[] => {
  const section = sectionData.value?.timeline
  if (!section || activeSection.value !== 'TIMELINE_AUDIT') return []
  return collectTimelineResourceLinks(section.items, null)
})

/** M155：复用已有 GET /appointments/{id}；与 Task 旁路并列。 */
const appointmentDetailLinks = computed((): AppointmentDetailLink[] => {
  const section = sectionData.value?.appointmentsVisits
  if (!section || activeSection.value !== 'APPOINTMENTS_VISITS') return []
  const raw = section.appointments
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.appointmentId
      if (typeof id !== 'string' || !id) return null
      return {
        appointmentId: id,
        type: String(row.type ?? '—'),
        status: String(row.status ?? '—'),
      }
    })
    .filter((item): item is AppointmentDetailLink => item != null)
})

/** M159：复用 GET /visits/{id}；与 Task 旁路并列。 */
const visitDetailLinks = computed((): VisitDetailLink[] => {
  const section = sectionData.value?.appointmentsVisits
  if (!section || activeSection.value !== 'APPOINTMENTS_VISITS') return []
  const raw = section.visits
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.visitId
      if (typeof id !== 'string' || !id) return null
      return {
        visitId: id,
        status: String(row.status ?? '—'),
        visitSequence: String(row.visitSequence ?? '—'),
      }
    })
    .filter((item): item is VisitDetailLink => item != null)
})

/** M160：复用 GET /contact-attempts/{id}；与 Task 旁路并列。 */
const contactAttemptDetailLinks = computed((): ContactAttemptDetailLink[] => {
  const section = sectionData.value?.appointmentsVisits
  if (!section || activeSection.value !== 'APPOINTMENTS_VISITS') return []
  const raw = section.contactAttempts
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.contactAttemptId
      if (typeof id !== 'string' || !id) return null
      return {
        contactAttemptId: id,
        channel: String(row.channel ?? '—'),
        resultCode: String(row.resultCode ?? '—'),
      }
    })
    .filter((item): item is ContactAttemptDetailLink => item != null)
})

/**
 * appointments/visits/contactAttempts 已有详情页时，仍保留 Task 旁路供现场操作入口。
 */
const appointmentVisitTaskLinks = computed((): RelatedTaskLink[] => {
  const section = sectionData.value?.appointmentsVisits
  if (!section || activeSection.value !== 'APPOINTMENTS_VISITS') return []
  const fromAppointments = collectRelatedTaskLinks(section.appointments, (row) => {
    return `查看预约关联任务 · ${statusLabel(String(row.status ?? '—'))}`
  })
  if (fromAppointments.length) return fromAppointments
  const fromVisits = collectRelatedTaskLinks(section.visits, (row) => {
    return `查看上门关联任务 · ${statusLabel(String(row.status ?? '—'))}`
  })
  if (fromVisits.length) return fromVisits
  return collectRelatedTaskLinks(section.contactAttempts, (row) => {
    return `查看联系关联任务 · ${statusLabel(String(row.resultCode ?? '—'))}`
  })
})

/** M155：复用已有 GET /form-submissions/{id}；与 Task 旁路并列。 */
const formSubmissionDetailLinks = computed((): FormSubmissionDetailLink[] => {
  const section = sectionData.value?.formsEvidence
  if (!section || activeSection.value !== 'FORMS_EVIDENCE') return []
  const raw = section.formSubmissions
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.submissionId
      if (typeof id !== 'string' || !id) return null
      return {
        submissionId: id,
        formKey: String(row.formKey ?? '—'),
        validationStatus: String(row.validationStatus ?? '—'),
      }
    })
    .filter((item): item is FormSubmissionDetailLink => item != null)
})

/** M156：复用已有 GET /evidence-items/{id}；与 Task 旁路并列。 */
const evidenceItemDetailLinks = computed((): EvidenceItemDetailLink[] => {
  const section = sectionData.value?.formsEvidence
  if (!section || activeSection.value !== 'FORMS_EVIDENCE') return []
  const raw = section.evidenceItems
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const id = row.evidenceItemId
      if (typeof id !== 'string' || !id) return null
      const latestRevisionId =
        typeof row.latestRevisionId === 'string' && row.latestRevisionId
          ? row.latestRevisionId
          : null
      const latestMimeType =
        typeof row.latestMimeType === 'string' && row.latestMimeType
          ? row.latestMimeType
          : null
      return {
        evidenceItemId: id,
        status: String(row.status ?? '—'),
        itemOrdinal: String(row.itemOrdinal ?? '—'),
        latestRevisionId,
        latestMimeType,
      }
    })
    .filter((item): item is EvidenceItemDetailLink => item != null)
})

/** M426：图片型资料的短时授权预览 URL（purpose=WORKSPACE_EVIDENCE_PREVIEW）。 */
const evidencePreviewUrls = ref<Record<string, string>>({})
const evidencePreviewErrors = ref<Record<string, string>>({})
const evidencePreviewLoading = ref(false)

const imageEvidenceItems = computed(() =>
  evidenceItemDetailLinks.value.filter(
    (item) =>
      item.latestRevisionId != null &&
      item.latestMimeType != null &&
      item.latestMimeType.startsWith('image/'),
  ),
)

async function loadEvidencePreviews() {
  evidencePreviewUrls.value = {}
  evidencePreviewErrors.value = {}
  if (activeSection.value !== 'FORMS_EVIDENCE' || imageEvidenceItems.value.length === 0) {
    evidencePreviewLoading.value = false
    return
  }
  evidencePreviewLoading.value = true
  try {
    await Promise.all(
      imageEvidenceItems.value.map(async (item) => {
        const revisionId = item.latestRevisionId
        if (!revisionId) return
        try {
          const auth = await authorizeEvidenceRevisionDownload(
            revisionId,
            'WORKSPACE_EVIDENCE_PREVIEW',
          )
          evidencePreviewUrls.value = {
            ...evidencePreviewUrls.value,
            [item.evidenceItemId]: auth.downloadUrl,
          }
        } catch (err) {
          evidencePreviewErrors.value = {
            ...evidencePreviewErrors.value,
            [item.evidenceItemId]:
              err instanceof Error ? err.message : '预览授权失败',
          }
        }
      }),
    )
  } finally {
    evidencePreviewLoading.value = false
  }
}

watch(
  () => [activeSection.value, evidenceItemDetailLinks.value.map((item) => item.evidenceItemId).join('|')],
  () => {
    void loadEvidencePreviews()
  },
)

/**
 * 表单定义/资料槽位仍无独立详情页；旁路到 Task。
 * formSubmissions 已有详情页，仍保留 Task 旁路供编排入口。
 */
const formsEvidenceTaskLinks = computed((): RelatedTaskLink[] => {
  const section = sectionData.value?.formsEvidence
  if (!section || activeSection.value !== 'FORMS_EVIDENCE') return []
  const fromSubmissions = collectRelatedTaskLinks(section.formSubmissions, (row) => {
    return `查看表单关联任务 · ${statusLabel(String(row.validationStatus ?? '—'))}`
  })
  if (fromSubmissions.length) return fromSubmissions
  const fromForms = collectRelatedTaskLinks(section.forms, () => {
    return '查看表单关联任务'
  })
  if (fromForms.length) return fromForms
  const fromItems = collectRelatedTaskLinks(section.evidenceItems, (row) => {
    return `查看资料关联任务 · ${statusLabel(String(row.status ?? '—'))}`
  })
  if (fromItems.length) return fromItems
  return collectRelatedTaskLinks(section.evidenceSlots, () => {
    return '查看资料要求关联任务'
  })
})


const orderCode = computed(() => workspace.value?.header.externalOrderCode || presentEmptyValue('not_provided'))
const workOrderPresentation = computed(() => presentWorkOrderStatus(workspace.value?.header.status))
const projectPresentation = computed(() =>
  presentEntityName({
    name: projectName.value,
    id: workspace.value?.header.projectId,
    loaded: directoryLoaded.value,
  }),
)
const clientLabel = computed(() => labelClientCode(workOrderDetail.value?.workOrder.clientCode ?? workspace.value?.header.clientCode as string | undefined))
const networkSelectOptions = computed(() =>
  (networkCandidates.value?.candidates ?? []).map((candidate) => ({
    value: candidate.networkId,
    label: candidate.networkName,
  })),
)
const selectedNetworkCandidate = computed(() =>
  networkCandidates.value?.candidates.find(
    (candidate) => candidate.networkId === networkAssigneeId.value,
  ),
)
const assignmentNetworkId = computed(() => {
  const value = workspace.value?.serviceAssignmentSummary?.networkId
  return typeof value === 'string' ? value : null
})
const assignmentTechnicianId = computed(() => {
  const value = workspace.value?.serviceAssignmentSummary?.technicianId
  return typeof value === 'string' ? value : null
})
const networkPresentation = computed(() => {
  const id = assignmentNetworkId.value
  const network = id ? networkDirectory.value.find((item) => item.id === id) : null
  return presentEntityName({
    name: network?.networkName,
    code: network?.networkCode,
    id,
    loaded: directoryLoaded.value,
  })
})
const technicianPresentation = computed(() => {
  const id = assignmentTechnicianId.value
  const technician = id ? technicianDirectory.value.find((item) => item.id === id) : null
  return presentEntityName({
    name: technician?.displayName,
    id,
    loaded: directoryLoaded.value,
  })
})
const serviceProductLabel = computed(() =>
  labelServiceProduct(
    workOrderDetail.value?.workOrder.serviceProductCode ??
      (fulfillmentSnapshot.value?.serviceProductCode as string | undefined),
  ),
)
const currentStageLabel = computed(() =>
  workspace.value?.currentTaskSummary?.stageCode
    ? statusLabel(workspace.value.currentTaskSummary.stageCode)
    : '尚未进入履约阶段',
)
const currentTaskLabel = computed(() =>
  workspace.value?.currentTaskSummary?.taskType
    ? statusLabel(workspace.value.currentTaskSummary.taskType)
    : '暂无进行中的任务',
)
const currentTaskStatusLabel = computed(() =>
  workspace.value?.currentTaskSummary?.status
    ? statusLabel(workspace.value.currentTaskSummary.status)
    : '未开始',
)
const canOpenNetworkAssignment = computed(
  () => !!workspace.value?.currentTaskSummary && !assignmentNetworkId.value,
)
const slaSummaryText = computed(() => {
  const sla = workspace.value?.slaSummary
  if (!sla) return presentEmptyValue('no_permission')
  const open = Number(sla.openCount ?? 0)
  const breached = Number(sla.breachedCount ?? 0)
  if (breached > 0) return `已超时 ${breached} 项，进行中 ${open} 项`
  if (open > 0) return `进行中 ${open} 项`
  return '暂无进行中的时效'
})

const businessProgressSteps = computed<BusinessProgressStep[]>(() => {
  const list = [...(stages.value?.stages ?? [])].sort((a, b) => a.sequenceNo - b.sequenceNo)
  const currentStageCode = workspace.value?.currentTaskSummary?.stageCode
  return list.map((stage) => {
    const label = statusLabel(stage.stageCode) || stage.stageCode
    let status: BusinessProgressStep['status'] = 'upcoming'
    if (stage.status === 'COMPLETED' || stage.completedAt) status = 'done'
    else if (
      stage.stageCode === currentStageCode ||
      stage.status === 'ACTIVE' ||
      stage.status === 'IN_PROGRESS'
    ) {
      status = 'current'
    }
    return {
      key: stage.id,
      label,
      status,
      hint:
        stage.stageCode === currentStageCode && workspace.value?.currentTaskSummary
          ? statusLabel(workspace.value.currentTaskSummary.taskType)
          : undefined,
    }
  })
})

const allowedActionItems = computed<AllowedActionItem[]>(() => {
  const actions = allowedActions.value?.actions ?? []
  return actions.slice(0, 5).map((action, index) => ({
    code: action.code,
    label: labelAction(action.code, action.label),
    primary: index === 0,
  }))
})

const recentTimelineItems = computed(() => (timelinePage.value?.items ?? []).slice(0, 5))

function openNetworkAssignment() {
  assignNetworkDrawerOpen.value = true
}

function onAllowedActionSelect(_code: string) {
  productTab.value = 'TASKS'
  onProductTabChange('TASKS')
}

watch(workOrderId, () => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})

onMounted(() => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})
</script>


<template>
  <DetailPageLayout
    title="工单详情"
    description="查看工单当前状态、履约进度、责任人、任务、资料、审核与外部回传信息。"
    eyebrow="工单运营 / 工单详情"
    :show-sticky="!!workspace"
    sticky-note="主操作来自服务端允许动作；复杂改派请使用专用流程。"
  >
    <template #status>
      <SemanticStatusTag v-if="workspace" :presentation="workOrderPresentation" />
    </template>
    <template #secondary-actions>
      <Button :loading="loading" @click="loadWorkspace">刷新</Button>
    </template>
    <template #primary-action>
      <Space v-if="workspace" wrap>
        <Button
          v-if="canOpenNetworkAssignment"
          type="primary"
          data-testid="open-network-assignment"
          @click="openNetworkAssignment"
        >
          分配网点
        </Button>
        <AllowedActionBar
          v-if="allowedActionItems.length"
          :actions="allowedActionItems"
          @select="onAllowedActionSelect"
        />
      </Space>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" />
      <Alert v-else-if="loading && !workspace" type="info" show-icon message="正在加载工单…" />
    </template>

    <template v-if="workspace" #summary>
      <div class="work-order-summary-grid" data-testid="work-order-summary-strip">
        <section>
          <span class="summary-label">工单编号 / 客户</span>
          <strong>{{ orderCode }}</strong>
          <span>
            <SensitiveText
              data-testid="workspace-masked-customer-name"
              :value="workspace.maskedCustomerName"
              :empty-text="presentEmptyValue('not_provided')"
            />
          </span>
          <SensitiveText
            data-testid="workspace-masked-customer-phone"
            :value="workspace.maskedCustomerPhone"
            :empty-text="presentEmptyValue('not_provided')"
          />
        </section>
        <section>
          <span class="summary-label">项目与服务</span>
          <RouterLink
            class="summary-primary-link"
            :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: workspace.header.projectId } }"
            aria-label="打开所属项目"
          >
            {{ projectPresentation.label }}
          </RouterLink>
          <span>{{ serviceProductLabel }}</span>
        </section>
        <section>
          <span class="summary-label">当前状态</span>
          <SemanticStatusTag :presentation="workOrderPresentation" />
          <span>{{ currentStageLabel }}</span>
        </section>
        <section>
          <span class="summary-label">当前任务</span>
          <strong>{{ currentTaskLabel }}</strong>
          <span>{{ currentTaskStatusLabel }}</span>
        </section>
        <section>
          <span class="summary-label">SLA</span>
          <strong :class="{ 'summary-risk': Number(workspace.slaSummary?.breachedCount ?? 0) > 0 }">
            {{ slaSummaryText }}
          </strong>
          <span>以服务端计时结果为准</span>
        </section>
        <section>
          <span class="summary-label">当前责任</span>
          <strong>{{ networkPresentation.label }}</strong>
          <span>{{ technicianPresentation.label }}</span>
        </section>
      </div>
    </template>

    <template v-if="workspace" #progress>
      <BusinessProgress title="履约进度" :steps="businessProgressSteps" />
      <p v-if="stages?.workflow" class="muted" style="margin-top: 8px">
        当前流程：{{ stages.workflow.status ? statusLabel(stages.workflow.status) : '尚未初始化' }}
      </p>
    </template>

    <div v-if="workspace" class="wo-workspace-body" data-testid="work-order-fulfillment-workspace">
      <div class="wo-workspace-body__main">
        <Card
          v-if="workspace.currentTaskSummary"
          title="当前任务"
          size="small"
          class="current-task-card"
          data-testid="current-task-card"
        >
          <div class="current-task-layout">
            <div class="current-task-layout__content">
              <div class="current-task-heading">
                <span class="current-task-icon" aria-hidden="true"><ToolOutlined /></span>
                <div>
                  <strong>{{ currentTaskLabel }}</strong>
                  <p>{{ currentStageLabel }} · {{ currentTaskStatusLabel }}</p>
                </div>
              </div>
              <dl class="current-task-facts">
                <div><dt>责任网点</dt><dd>{{ networkPresentation.label }}</dd></div>
                <div><dt>责任师傅</dt><dd>{{ technicianPresentation.label }}</dd></div>
                <div><dt>SLA</dt><dd>{{ slaSummaryText }}</dd></div>
                <div><dt>服务地址</dt><dd><SensitiveText data-testid="workspace-masked-service-address" :value="workspace.maskedServiceAddress" :empty-text="presentEmptyValue('not_provided')" /></dd></div>
              </dl>
            </div>
            <aside class="current-task-layout__actions" aria-label="当前任务允许操作">
              <span class="summary-label">允许操作</span>
              <AllowedActionBar
                v-if="allowedActions"
                :actions="allowedActionItems"
                empty-text="当前无可执行动作"
                @select="onAllowedActionSelect"
              />
              <Button
                type="primary"
                @click="router.push({ name: 'ADMIN.TASK.DETAIL', params: { id: workspace.currentTaskSummary.taskId } })"
              >
                打开任务工作区
              </Button>
            </aside>
          </div>
          <div v-if="!allowedActions" style="margin-top: 12px">
            <Alert
              v-if="allowedActionsError"
              type="error"
              show-icon
              :message="allowedActionsError"
            />
            <p v-else class="muted">暂无允许动作或无权读取</p>
          </div>
        </Card>

    <Tabs v-model:activeKey="productTab" @change="onProductTabChange">
      <TabPane key="overview" tab="基本信息">
        <Space direction="vertical" style="width: 100%" :size="16">
          <Card title="配置来源" size="small">
            <template v-if="fulfillmentSnapshot">
              <Descriptions bordered size="small" :column="2">
                <Descriptions.Item label="工单类型">
                  {{ labelServiceProduct(fulfillmentSnapshot.serviceProductCode) }}
                </Descriptions.Item>
                <Descriptions.Item label="履约方案">
                  {{ fulfillmentSnapshot.profileName || '历史履约配置' }}
                </Descriptions.Item>
                <Descriptions.Item label="履约版本">
                  {{ fulfillmentSnapshot.fulfillmentVersion || '—' }}
                </Descriptions.Item>
                <Descriptions.Item label="配置包版本">
                  {{ fulfillmentSnapshot.configurationBundleVersion || '—' }}
                </Descriptions.Item>
              </Descriptions>
              <Alert
                v-if="fulfillmentSnapshot.legacyExplanation"
                type="info"
                show-icon
                message="本工单使用创建时冻结的历史履约配置。"
                style="margin-top: 12px"
              />
              <Button
                style="margin-top: 12px"
                @click="
                  router.push({
                    name: 'ADMIN.WORKORDER.FULFILLMENT_SNAPSHOT',
                    params: { id: workOrderId },
                  })
                "
              >
                查看本工单配置快照
              </Button>
            </template>
            <p v-else class="muted">配置来源暂不可用（可能缺少 snapshot 读权限）。</p>
          </Card>
          <Card title="影子试算（非正式）" size="small" data-testid="pricing-shadow-panel">
            <SemanticStatusTag :presentation="presentPricingStatus('SHADOW')" />
            <p class="muted">只读影子试算，不提供结算落账。</p>
            <Alert v-if="pricingSnapshotsError" type="error" show-icon :message="pricingSnapshotsError" />
            <ul v-if="pricingSnapshots?.items?.length" class="pricing-list">
              <li v-for="item in pricingSnapshots.items" :key="item.snapshotId">
                {{ item.pricingKey }} · {{ item.currency }}
                {{ (item.totalAmountMinor / 100).toFixed(2) }}
                <SemanticStatusTag :presentation="presentPricingStatus(item.mode)" />
                <small>{{ formatDateTimeDisplay(item.createdAt) }}</small>
              </li>
            </ul>
            <p v-else class="muted">{{ pricingSnapshots?.emptyHint || '暂无影子试算快照。' }}</p>
          </Card>

        </Space>
      </TabPane>

      <TabPane key="TASKS" tab="任务记录">
        <div v-if="taskPage?.items?.length" class="task-record-list">
          <RouterLink
            v-for="item in taskPage.items"
            :key="item.id"
            class="task-record-list__item"
            :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.id } }"
          >
            <span>
              <strong>{{ statusLabel(item.taskType) }}</strong>
              <small>{{ statusLabel(item.stageCode) }} · {{ statusLabel(item.taskKind) }}</small>
            </span>
            <SemanticStatusTag
              :presentation="{
                label: statusLabel(item.status),
                semantic: item.status === 'COMPLETED' ? 'success' : 'info',
                icon: item.status === 'COMPLETED' ? 'check' : 'info',
              }"
            />
          </RouterLink>
        </div>
        <p v-else class="muted">暂无任务记录</p>
        <div v-if="activeSection === 'TASKS'">
          <p v-if="sectionError" class="error">{{ sectionError }}</p>
          <p v-else-if="sectionLoading">区块加载中…</p>
        </div>
      </TabPane>

      <TabPane key="APPOINTMENTS_VISITS" tab="预约与上门">
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <p v-else class="muted">预约、联系尝试与上门记录见关联明细；缺字段显示「未提供」。</p>
      </TabPane>

      <TabPane key="FORMS_EVIDENCE" tab="表单资料">
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <template v-else>
          <p class="muted">
            图片资料通过短时授权预览展示；非图片仅保留详情深链。不得缓存永久 URL。
          </p>
          <section
            v-if="evidenceItemDetailLinks.length"
            class="evidence-preview-grid"
            data-testid="workspace-evidence-previews"
            aria-label="资料预览"
          >
            <article
              v-for="item in evidenceItemDetailLinks"
              :key="item.evidenceItemId"
              class="evidence-preview-card"
              data-testid="workspace-evidence-preview-card"
            >
              <header class="evidence-preview-card__head">
                <span>#{{ item.itemOrdinal }} · {{ statusLabel(item.status) }}</span>
                <RouterLink
                  :to="{ name: 'ADMIN.EVIDENCE_ITEM.DETAIL', params: { id: item.evidenceItemId } }"
                  data-testid="workspace-evidence-item-link"
                >
                  打开资料详情
                </RouterLink>
              </header>
              <template v-if="item.latestRevisionId && item.latestMimeType?.startsWith('image/')">
                <p v-if="evidencePreviewLoading && !evidencePreviewUrls[item.evidenceItemId]" class="muted">
                  预览授权中…
                </p>
                <p
                  v-else-if="evidencePreviewErrors[item.evidenceItemId]"
                  class="error"
                  data-testid="workspace-evidence-preview-error"
                >
                  {{ evidencePreviewErrors[item.evidenceItemId] }}
                </p>
                <img
                  v-else-if="evidencePreviewUrls[item.evidenceItemId]"
                  class="evidence-preview-card__thumb"
                  data-testid="workspace-evidence-preview-image"
                  :src="evidencePreviewUrls[item.evidenceItemId]"
                  :alt="`资料预览 ${item.itemOrdinal}`"
                />
                <p v-else class="muted">暂无预览</p>
              </template>
              <p v-else class="muted" data-testid="workspace-evidence-preview-non-image">
                {{
                  item.latestMimeType
                    ? `非图片类型（${item.latestMimeType}），请打开详情`
                    : '尚无最新修订，请打开详情'
                }}
              </p>
            </article>
          </section>
          <p v-else class="muted" data-testid="workspace-evidence-previews-empty">
            暂无资料项摘要
          </p>
          <p v-if="formSubmissionDetailLinks.length" class="links">
            <RouterLink
              v-for="item in formSubmissionDetailLinks"
              :key="item.submissionId"
              :to="{ name: 'ADMIN.FORM_SUBMISSION.DETAIL', params: { id: item.submissionId } }"
            >
              {{ item.formKey }} / {{ statusLabel(item.validationStatus) }}
            </RouterLink>
          </p>
        </template>
      </TabPane>

      <TabPane key="REVIEWS_CORRECTIONS" tab="审核与整改">
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <template v-else>
          <section
            v-if="reviewCaseLinks.length"
            class="review-records"
            data-testid="workspace-review-records"
            aria-label="审核决策记录"
          >
            <article
              v-for="item in reviewCaseLinks"
              :key="item.reviewCaseId"
              class="review-case-card"
              data-testid="workspace-review-case"
            >
              <header class="review-case-card__head">
                <div>
                  <strong>{{ statusLabel(item.origin) }}</strong>
                  ·
                  <SemanticStatusTag :presentation="presentReviewStatus(item.status)" />
                </div>
                <RouterLink
                  :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.reviewCaseId } }"
                  data-testid="workspace-review-case-link"
                >
                  打开审核详情
                </RouterLink>
              </header>
              <table
                v-if="item.decisions.length"
                class="decision-table"
                data-testid="workspace-review-decisions"
              >
                <thead>
                  <tr>
                    <th scope="col">轮次</th>
                    <th scope="col">裁决</th>
                    <th scope="col">来源</th>
                    <th scope="col">原因码</th>
                    <th scope="col">裁决时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="decision in item.decisions"
                    :key="decision.reviewDecisionId"
                    data-testid="workspace-review-decision-row"
                  >
                    <td>{{ decision.decisionOrdinal }}</td>
                    <td>
                      <SemanticStatusTag
                        :presentation="presentReviewStatus(decision.decision)"
                      />
                    </td>
                    <td>{{ statusLabel(decision.decisionSource) }}</td>
                    <td>
                      {{
                        decision.reasonCodes.length
                          ? decision.reasonCodes.map((code) => statusLabel(code)).join('、')
                          : presentEmptyValue('not_provided')
                      }}
                    </td>
                    <td>{{ formatDateTimeDisplay(decision.decidedAt) }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="muted" data-testid="workspace-review-decisions-empty">
                暂无决策记录
              </p>
            </article>
          </section>
          <p v-else class="muted" data-testid="workspace-review-records-empty">
            暂无审核案例摘要
          </p>

          <section
            v-if="correctionCaseLinks.length"
            class="correction-records"
            data-testid="workspace-correction-records"
            aria-label="整改补传记录"
          >
            <article
              v-for="item in correctionCaseLinks"
              :key="item.correctionCaseId"
              class="review-case-card"
              data-testid="workspace-correction-case"
            >
              <header class="review-case-card__head">
                <div>
                  <strong>整改</strong>
                  ·
                  <SemanticStatusTag :presentation="presentCorrectionStatus(item.status)" />
                </div>
                <RouterLink
                  :to="{ name: 'ADMIN.CORRECTION.DETAIL', params: { id: item.correctionCaseId } }"
                  data-testid="workspace-correction-case-link"
                >
                  打开整改详情
                </RouterLink>
              </header>
              <table
                v-if="item.resubmissions.length"
                class="decision-table"
                data-testid="workspace-correction-resubmissions"
              >
                <thead>
                  <tr>
                    <th scope="col">轮次</th>
                    <th scope="col">资料快照</th>
                    <th scope="col">提交时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="resubmission in item.resubmissions"
                    :key="resubmission.correctionResubmissionId"
                    data-testid="workspace-correction-resubmission-row"
                  >
                    <td>{{ resubmission.resubmissionOrdinal }}</td>
                    <td>
                      <RouterLink
                        :to="{
                          name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
                          params: { id: resubmission.evidenceSetSnapshotId },
                        }"
                      >
                        第 {{ resubmission.resubmissionOrdinal }} 轮资料快照
                      </RouterLink>
                    </td>
                    <td>{{ formatDateTimeDisplay(resubmission.submittedAt) }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="muted">暂无补传记录</p>
            </article>
          </section>
        </template>
      </TabPane>

      <TabPane key="FINAL_REVIEW" tab="平台终审">
        <FinalReviewWorkspace :work-order-id="workOrderId" />
      </TabPane>

      <TabPane key="TIMELINE_AUDIT" tab="操作日志">
        <ol v-if="activity?.items?.length" class="product-timeline">
          <li v-for="(item, index) in activity.items" :key="index">
            <span class="product-timeline__dot" aria-hidden="true"></span>
            <div>
              <strong>{{ presentWorkOrderTimelineEvent(item.eventType || item.type).label }}</strong>
              <span>{{ formatDateTimeDisplay(item.occurredAt) }}</span>
            </div>
          </li>
        </ol>
        <p v-else class="muted">暂无业务动态</p>
      </TabPane>

      <TabPane key="INTEGRATION" tab="外部回传">
        <Alert
          type="info"
          show-icon
          message="车企集成与外部回传"
          description="查看外部工单接收、业务状态回传和客户确认记录。"
        />
        <p v-if="sectionLoading">区块加载中…</p>
        <p v-else-if="sectionError" class="error">{{ sectionError }}</p>
      </TabPane>

    </Tabs>

    <div v-if="productTab !== 'FINAL_REVIEW' && productTab !== 'overview'" class="related-business-links">
      <p v-if="inboundEnvelopeLinks.length" class="links inbound-links">
        外部工单接收记录：
        <RouterLink
          v-for="item in inboundEnvelopeLinks"
          :key="item.inboundEnvelopeId"
          :to="{ name: 'ADMIN.INTEGRATION.INBOUND.DETAIL', params: { id: item.inboundEnvelopeId } }"
        >
          查看接收记录 · {{ statusLabel(item.processingStatus) }}
        </RouterLink>
      </p>
      <p v-if="outboundDeliveryLinks.length" class="links outbound-links">
        外部业务回传记录：
        <RouterLink
          v-for="item in outboundDeliveryLinks"
          :key="item.deliveryId"
          :to="{ name: 'ADMIN.INTEGRATION.DETAIL', params: { id: item.deliveryId } }"
        >
          查看回传记录 · {{ statusLabel(item.status) }} · {{ item.externalOrderCode }}
        </RouterLink>
      </p>
      <p v-if="reviewCorrectionCrossLinks.length" class="links review-cross-links">
        <RouterLink
          v-for="item in reviewCorrectionCrossLinks"
          :key="item.key"
          :to="{ name: item.routeName, params: { id: item.resourceId } }"
        >
          {{ item.label }}
        </RouterLink>
      </p>
      <p v-if="canonicalMessageLinks.length" class="links canonical-links">
        <RouterLink
          v-for="item in canonicalMessageLinks"
          :key="item.canonicalMessageId"
          :to="{ name: 'ADMIN.INTEGRATION.CANONICAL.DETAIL', params: { id: item.canonicalMessageId } }"
        >
          查看业务消息 · {{ statusLabel(item.processingStatus) }}
        </RouterLink>
      </p>
      <p v-if="outboundCrossLinks.length" class="links outbound-cross-links">
        <RouterLink
          v-for="item in outboundCrossLinks"
          :key="item.key"
          :to="{ name: item.routeName, params: { id: item.resourceId } }"
        >
          {{ item.label }}
        </RouterLink>
      </p>
      <p v-if="taskSectionLinks.length" class="links">
        <RouterLink
          v-for="item in taskSectionLinks"
          :key="item.taskId"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
        >
          {{ statusLabel(item.taskType) }} / {{ statusLabel(item.status) }}
        </RouterLink>
      </p>
      <p v-if="appointmentDetailLinks.length" class="links">
        <RouterLink
          v-for="item in appointmentDetailLinks"
          :key="item.appointmentId"
          :to="{ name: 'ADMIN.APPOINTMENT.DETAIL', params: { id: item.appointmentId } }"
        >
          {{ statusLabel(item.status) }}
        </RouterLink>
      </p>
      <p v-if="visitDetailLinks.length" class="links">
        <RouterLink
          v-for="item in visitDetailLinks"
          :key="item.visitId"
          :to="{ name: 'ADMIN.VISIT.DETAIL', params: { id: item.visitId } }"
        >
          {{ statusLabel(item.status) }}
        </RouterLink>
      </p>
      <p v-if="contactAttemptDetailLinks.length" class="links">
        <RouterLink
          v-for="item in contactAttemptDetailLinks"
          :key="item.contactAttemptId"
          :to="{ name: 'ADMIN.CONTACT_ATTEMPT.DETAIL', params: { id: item.contactAttemptId } }"
        >
          {{ statusLabel(item.resultCode) }}
        </RouterLink>
      </p>
      <p v-if="appointmentVisitTaskLinks.length" class="links">
        <RouterLink
          v-for="item in appointmentVisitTaskLinks"
          :key="item.key"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
        >
          {{ item.label }}
        </RouterLink>
      </p>
      <p v-if="formsEvidenceTaskLinks.length" class="links">
        <RouterLink
          v-for="item in formsEvidenceTaskLinks"
          :key="item.key"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
        >
          {{ item.label }}
        </RouterLink>
      </p>
      <p v-if="timelineResourceLinks.length" class="links">
        <RouterLink
          v-for="item in timelineResourceLinks"
          :key="item.key"
          :to="{ name: item.routeName, params: { id: item.resourceId } }"
        >
          {{ item.label }}
        </RouterLink>
      </p>
    </div>
      </div>

      <aside
        class="work-order-context-rail"
        data-testid="work-order-context-rail"
        aria-label="工单处理上下文"
      >
        <Card title="风险与提醒" size="small">
          <dl class="context-facts">
            <div><dt>SLA</dt><dd>{{ slaSummaryText }}</dd></div>
            <div>
              <dt>运营异常</dt>
              <dd v-if="Number(workspace.exceptionSummary?.openCount ?? 0) > 0" class="context-warning">
                {{ workspace.exceptionSummary?.openCount }} 条待处理
              </dd>
              <dd v-else class="context-success">无待处理异常</dd>
            </div>
          </dl>
          <RouterLink
            v-if="Number(workspace.exceptionSummary?.openCount ?? 0) > 0"
            :to="{ name: 'ADMIN.EXCEPTION.QUEUE', query: { workOrderId, status: 'OPEN' } }"
          >
            查看异常详情
          </RouterLink>
          <RouterLink
            v-else-if="slaPage?.items?.[0]"
            :to="{ name: 'ADMIN.SLA.DETAIL', params: { id: slaPage.items[0].slaInstanceId } }"
          >
            查看 SLA 详情
          </RouterLink>
        </Card>

        <Card title="当前责任链" size="small">
          <ol class="responsibility-chain">
            <li><span>当前任务</span><strong>{{ currentTaskLabel }}</strong></li>
            <li><span>责任网点</span><strong>{{ networkPresentation.label }}</strong></li>
            <li><span>责任师傅</span><strong>{{ technicianPresentation.label }}</strong></li>
          </ol>
        </Card>

        <Card title="外部集成信息" size="small">
          <dl class="context-facts">
            <div><dt>来源系统</dt><dd>{{ clientLabel }}</dd></div>
            <div><dt>接收时间</dt><dd>{{ formatDateTimeDisplay(workspace.header.receivedAt) }}</dd></div>
            <div><dt>履约版本</dt><dd>{{ fulfillmentSnapshot?.fulfillmentVersion || '尚未生成' }}</dd></div>
          </dl>
        </Card>

        <Card title="最近业务动态" size="small">
          <ol v-if="recentTimelineItems.length" class="product-timeline product-timeline--compact">
            <li v-for="(item, index) in recentTimelineItems" :key="index">
              <span class="product-timeline__dot" aria-hidden="true"></span>
              <div>
                <strong>{{ presentWorkOrderTimelineEvent(item.eventType).label }}</strong>
                <span>{{ formatDateTimeDisplay(item.occurredAt) }}</span>
              </div>
            </li>
          </ol>
          <p v-else class="muted">暂无业务动态</p>
        </Card>
      </aside>
    </div>

    <Drawer
      v-model:open="assignNetworkDrawerOpen"
      title="分配责任网点"
      placement="right"
      :width="520"
      destroy-on-close
    >
      <div class="assignment-drawer">
        <Alert
          type="info"
          show-icon
          message="系统只展示满足当前项目、服务区域、业务类型和容量要求的网点。"
        />
        <Alert
          v-if="networkCandidatesError"
          type="error"
          show-icon
          :message="networkCandidatesError"
        />
        <Alert
          v-else-if="networkCandidates?.emptyReason"
          type="warning"
          show-icon
          :message="networkCandidates.emptyReason"
        />
        <p v-else class="muted">
          {{ networkCandidates?.rankingExplanation || '正在读取责任网点候选…' }}
        </p>
        <label class="field">
          <span>责任网点</span>
          <Select
            v-model:value="networkAssigneeId"
            show-search
            allow-clear
            style="width: 100%"
            placeholder="选择符合条件的网点"
            :loading="networkCandidatesLoading"
            :options="networkSelectOptions"
            :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          />
        </label>
        <Descriptions v-if="selectedNetworkCandidate" bordered size="small" :column="1">
          <Descriptions.Item label="网点名称">{{ selectedNetworkCandidate.networkName }}</Descriptions.Item>
          <Descriptions.Item label="服务覆盖">{{ selectedNetworkCandidate.coverageSummary }}</Descriptions.Item>
          <Descriptions.Item label="剩余容量">{{ selectedNetworkCandidate.remainingCapacity }}</Descriptions.Item>
          <Descriptions.Item label="推荐说明">{{ selectedNetworkCandidate.recommendationSummary }}</Descriptions.Item>
        </Descriptions>
        <Alert v-if="assignNetworkError" type="error" show-icon :message="assignNetworkError" />
        <Alert v-if="assignNetworkMessage" type="success" show-icon :message="assignNetworkMessage" />
        <div class="assignment-drawer__actions">
          <Button @click="assignNetworkDrawerOpen = false">取消</Button>
          <Button
            type="primary"
            data-testid="assign-network"
            :loading="assignNetworkBusy"
            :disabled="!workspace?.currentTaskSummary || !selectedNetworkCandidate"
            @click="runAssignNetwork"
          >
            确认分配
          </Button>
        </div>
      </div>
    </Drawer>
  </DetailPageLayout>
</template>

<style scoped>
.wo-workspace-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}
.wo-workspace-body__main {
  min-width: 0;
}
.work-order-summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}
.work-order-summary-grid > section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 2px 18px;
  color: var(--sos-color-text-secondary);
}
.work-order-summary-grid > section:first-child {
  padding-left: 0;
}
.work-order-summary-grid > section + section {
  border-left: 1px solid var(--sos-color-border-light, #eaedf0);
}
.work-order-summary-grid strong,
.summary-primary-link {
  overflow: hidden;
  color: var(--sos-color-text-primary);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-label {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
.summary-risk {
  color: var(--sos-color-status-critical-fg, #dc2626) !important;
}
.current-task-card {
  margin-bottom: 16px;
}
.current-task-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px;
  gap: 20px;
}
.current-task-layout__content {
  min-width: 0;
}
.current-task-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.current-task-heading strong {
  color: var(--sos-color-text-primary);
  font-size: 16px;
}
.current-task-heading p {
  margin: 3px 0 0;
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
.current-task-icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--sos-primary-100);
  color: var(--sos-primary-700);
  font-size: 18px;
}
.current-task-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 24px;
  margin: 0;
}
.current-task-facts div:last-child {
  grid-column: 1 / -1;
}
.current-task-facts dt,
.context-facts dt {
  margin-bottom: 4px;
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
.current-task-facts dd,
.context-facts dd {
  margin: 0;
  color: var(--sos-color-text-primary);
  font-size: 13px;
  font-weight: 500;
}
.current-task-layout__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 16px;
  border-left: 1px solid var(--sos-color-border-light, #eaedf0);
}
.current-task-layout__actions :deep(.sos-allowed-action-bar) {
  display: grid;
}
.current-task-layout__actions :deep(.sos-allowed-action-bar__btn) {
  width: 100%;
}
.work-order-context-rail {
  display: grid;
  gap: 12px;
}
.context-facts {
  display: grid;
  gap: 12px;
  margin: 0 0 10px;
}
.context-warning {
  color: var(--sos-color-status-warning-fg, #d97706) !important;
}
.context-success {
  color: var(--sos-color-status-success-fg, #16a34a) !important;
}
.responsibility-chain {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}
.responsibility-chain li {
  position: relative;
  display: grid;
  gap: 3px;
  padding: 0 0 16px 22px;
}
.responsibility-chain li::before {
  position: absolute;
  top: 4px;
  left: 2px;
  width: 9px;
  height: 9px;
  border: 2px solid var(--sos-primary-500);
  border-radius: 50%;
  background: #fff;
  content: '';
}
.responsibility-chain li:not(:last-child)::after {
  position: absolute;
  top: 15px;
  bottom: 0;
  left: 6px;
  width: 1px;
  background: var(--sos-primary-200);
  content: '';
}
.responsibility-chain span {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
.responsibility-chain strong {
  color: var(--sos-color-text-primary);
  font-size: 13px;
}
.product-timeline {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.product-timeline li {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
}
.product-timeline__dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--sos-primary-600);
}
.product-timeline li div {
  display: grid;
  gap: 2px;
}
.product-timeline strong {
  color: var(--sos-color-text-primary);
  font-size: 13px;
}
.product-timeline span:not(.product-timeline__dot) {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
.task-record-list {
  display: grid;
  gap: 8px;
}
.task-record-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--sos-color-border-light, #eaedf0);
  border-radius: 6px;
  background: var(--sos-color-surface-card);
}
.task-record-list__item > span:first-child {
  display: grid;
  gap: 4px;
}
.task-record-list__item small {
  color: var(--sos-color-text-tertiary);
}
.assignment-drawer {
  display: grid;
  gap: 16px;
}
.assignment-drawer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}
@media (max-width: 1280px) {
  .wo-workspace-body {
    grid-template-columns: 1fr;
  }
  .work-order-summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    row-gap: 16px;
  }
  .work-order-summary-grid > section:nth-child(4) {
    border-left: 0;
  }
}
@media (max-width: 900px) {
  .work-order-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .work-order-summary-grid > section:nth-child(odd) {
    border-left: 0;
  }
  .current-task-layout {
    grid-template-columns: 1fr;
  }
  .current-task-layout__actions {
    padding: 16px 0 0;
    border-top: 1px solid var(--sos-color-border-light, #eaedf0);
    border-left: 0;
  }
}
.muted { margin: 0 0 8px; color: var(--sos-color-text-tertiary, #7b8494); font-size: 13px; }
.field { display: grid; gap: 6px; margin-bottom: 12px; font-size: 13px; }
.links { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 12px; }
.review-records,
.correction-records {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}
.review-case-card {
  border: 1px solid var(--sos-color-border-default, #d9dee7);
  border-radius: var(--sos-radius-md, 8px);
  background: var(--sos-color-surface-card, #fff);
  padding: 12px 14px;
}
.review-case-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}
.decision-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.decision-table th,
.decision-table td {
  border-top: 1px solid var(--sos-color-border-default, #e5e9f0);
  padding: 8px 6px;
  text-align: left;
  vertical-align: top;
}
.decision-table th {
  color: var(--sos-color-text-secondary, #5b6575);
  font-weight: 600;
}
.evidence-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  margin: 12px 0 16px;
}
.evidence-preview-card {
  border: 1px solid var(--sos-color-border-default, #d9dee7);
  border-radius: var(--sos-radius-md, 8px);
  background: var(--sos-color-surface-card, #fff);
  padding: 10px 12px;
  display: grid;
  gap: 8px;
}
.evidence-preview-card__head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.evidence-preview-card__thumb {
  width: 100%;
  max-height: 180px;
  object-fit: contain;
  background: var(--sos-color-surface-subtle, #f5f7fa);
  border-radius: 6px;
}
.pricing-list { margin: 8px 0 0; padding-left: 18px; }
.error { color: var(--sos-color-status-critical-fg); }
.wo-workspace-body :deep(h3) {
  margin: 0 0 8px;
  font-size: 14px;
}
.wo-workspace-body section + section {
  margin-top: 14px;
}
.card { background: #fff; padding: 12px; border-radius: 8px; }
.related-business-links { margin-top: 12px; }
</style>
