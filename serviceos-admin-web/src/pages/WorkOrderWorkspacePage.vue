<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
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
  type WorkOrderDetail,
  type WorkOrderTaskPage,
  type WorkOrderTimelinePage,
  type WorkflowExecutionProjection,
} from '../api/workOrderDetail'
import { getTaskAllowedActions, type TaskAllowedActions } from '../api/tasks'
import { manualAssignServiceAssignments } from '../api/dispatch'
import TaskCommandPanel from '../components/TaskCommandPanel.vue'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))

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
const networkAssigneeId = ref('admin-pilot-network-1')
const technicianAssigneeId = ref('06b612f3-a901-4b0e-bd90-86b4259cc087')
const assignBusinessType = ref('INSTALLATION')
const manualAssignBusy = ref(false)
const manualAssignError = ref<string | null>(null)
const manualAssignMessage = ref<string | null>(null)

async function runManualAssign() {
  const taskId = workspace.value?.currentTaskSummary?.taskId
  if (!taskId) {
    manualAssignError.value = '无当前任务，无法人工初派'
    return
  }
  manualAssignBusy.value = true
  manualAssignError.value = null
  manualAssignMessage.value = null
  try {
    const result = await manualAssignServiceAssignments(taskId, {
      networkAssigneeId: networkAssigneeId.value.trim(),
      technicianAssigneeId: technicianAssigneeId.value.trim(),
      businessType: assignBusinessType.value.trim(),
    })
    const receipt = result.data
    manualAssignMessage.value =
      `已初派 network=${receipt.networkAssigneeId} tech=${receipt.technicianAssigneeId}`
    await loadWorkspace()
  } catch (err) {
    manualAssignError.value = err instanceof Error ? err.message : '人工初派失败'
  } finally {
    manualAssignBusy.value = false
  }
}

const sections: SectionCode[] = [
  'TASKS',
  'TIMELINE_AUDIT',
  'APPOINTMENTS_VISITS',
  'FORMS_EVIDENCE',
  'REVIEWS_CORRECTIONS',
  'INTEGRATION',
]

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
      loadAllowedActions(ws.currentTaskSummary?.taskId),
      loadSlaInstances(),
      loadAuthorityProjections(),
    ])
    const firstAvailable = sections.find(
      (code) => ws.sectionAvailability[code] === 'AVAILABLE' || ws.sectionAvailability[code] === 'EMPTY',
    )
    activeSection.value = firstAvailable ?? 'TASKS'
    await loadSection(activeSection.value)
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
  } finally {
    loading.value = false
  }
}

async function loadSection(section: SectionCode) {
  activeSection.value = section
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

const sectionPreview = computed(() => {
  const data = sectionData.value
  if (!data) return '—'
  const payload =
    data.tasks ??
    data.timeline ??
    data.appointmentsVisits ??
    data.formsEvidence ??
    data.reviewsCorrections ??
    data.integration
  return JSON.stringify(payload, null, 2)
})

type InboundEnvelopeLink = {
  inboundEnvelopeId: string
  messageType: string
  processingStatus: string
  resultCode: string | null
}

type OutboundDeliveryLink = {
  deliveryId: string
  businessMessageType: string
  status: string
  externalOrderCode: string
}

type ReviewCaseLink = {
  reviewCaseId: string
  origin: string
  status: string
}

type CorrectionCaseLink = {
  correctionCaseId: string
  status: string
  sourceReviewCaseId: string
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
}

/** 仅映射已有 Admin 详情路由；无对等页的 resourceType 不渲染。 */
const TIMELINE_RESOURCE_ROUTES: Record<string, string> = {
  WorkOrder: 'ADMIN.WORKORDER.WORKSPACE',
  Task: 'ADMIN.TASK.DETAIL',
  Appointment: 'ADMIN.APPOINTMENT.DETAIL',
  Visit: 'ADMIN.VISIT.DETAIL',
  ContactAttempt: 'ADMIN.CONTACT_ATTEMPT.DETAIL',
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
      return {
        inboundEnvelopeId: id,
        messageType: String(row.messageType ?? '—'),
        processingStatus: String(row.processingStatus ?? '—'),
        resultCode: row.resultCode == null ? null : String(row.resultCode),
      }
    })
    .filter((item): item is InboundEnvelopeLink => item != null)
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
      if (typeof id !== 'string' || !id) return null
      return {
        deliveryId: id,
        businessMessageType: String(row.businessMessageType ?? '—'),
        status: String(row.status ?? '—'),
        externalOrderCode: String(row.externalOrderCode ?? id),
      }
    })
    .filter((item): item is OutboundDeliveryLink => item != null)
})

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
      if (typeof id !== 'string' || !id) return null
      return {
        reviewCaseId: id,
        origin: String(row.origin ?? '—'),
        status: String(row.status ?? '—'),
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
      if (typeof id !== 'string' || !id) return null
      return {
        correctionCaseId: id,
        status: String(row.status ?? '—'),
        sourceReviewCaseId: String(row.sourceReviewCaseId ?? ''),
      }
    })
    .filter((item): item is CorrectionCaseLink => item != null)
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

/** 按需 TIMELINE_AUDIT：仅对已有详情页的 resourceType 生成深链。 */
const timelineResourceLinks = computed((): TimelineResourceLink[] => {
  const section = sectionData.value?.timeline
  if (!section || activeSection.value !== 'TIMELINE_AUDIT') return []
  const raw = section.items
  if (!Array.isArray(raw)) return []
  const seen = new Set<string>()
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const row = item as Record<string, unknown>
      const resourceType = typeof row.resourceType === 'string' ? row.resourceType : ''
      const resourceId = typeof row.resourceId === 'string' ? row.resourceId : ''
      const routeName = TIMELINE_RESOURCE_ROUTES[resourceType]
      if (!routeName || !resourceId) return null
      const dedupeKey = `${resourceType}:${resourceId}`
      if (seen.has(dedupeKey)) return null
      seen.add(dedupeKey)
      const eventType = String(row.eventType ?? '—')
      const resourceCode =
        typeof row.resourceCode === 'string' && row.resourceCode ? row.resourceCode : resourceId
      return {
        key: dedupeKey,
        routeName,
        resourceId,
        eventType,
        resourceType,
        label: `${eventType} / ${resourceType} / ${resourceCode}`,
      }
    })
    .filter((item): item is TimelineResourceLink => item != null)
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
  const fromAppointments = collectRelatedTaskLinks(section.appointments, (row, taskId) => {
    return `appointment / ${String(row.type ?? '—')} / ${String(row.status ?? '—')} / ${taskId}`
  })
  if (fromAppointments.length) return fromAppointments
  const fromVisits = collectRelatedTaskLinks(section.visits, (row, taskId) => {
    return `visit / ${String(row.status ?? '—')} / ${taskId}`
  })
  if (fromVisits.length) return fromVisits
  return collectRelatedTaskLinks(section.contactAttempts, (row, taskId) => {
    return `contact / ${String(row.channel ?? '—')} / ${String(row.resultCode ?? '—')} / ${taskId}`
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
      return {
        evidenceItemId: id,
        status: String(row.status ?? '—'),
        itemOrdinal: String(row.itemOrdinal ?? '—'),
      }
    })
    .filter((item): item is EvidenceItemDetailLink => item != null)
})

/**
 * 表单定义/资料槽位仍无独立详情页；旁路到 Task。
 * formSubmissions 已有详情页，仍保留 Task 旁路供编排入口。
 */
const formsEvidenceTaskLinks = computed((): RelatedTaskLink[] => {
  const section = sectionData.value?.formsEvidence
  if (!section || activeSection.value !== 'FORMS_EVIDENCE') return []
  const fromSubmissions = collectRelatedTaskLinks(section.formSubmissions, (row, taskId) => {
    return `submission / ${String(row.formKey ?? '—')} / ${String(row.validationStatus ?? '—')} / ${taskId}`
  })
  if (fromSubmissions.length) return fromSubmissions
  const fromForms = collectRelatedTaskLinks(section.forms, (row, taskId) => {
    return `form / ${String(row.formKey ?? '—')} / ${taskId}`
  })
  if (fromForms.length) return fromForms
  const fromItems = collectRelatedTaskLinks(section.evidenceItems, (row, taskId) => {
    return `evidence-item / ${String(row.status ?? '—')} / ${taskId}`
  })
  if (fromItems.length) return fromItems
  return collectRelatedTaskLinks(section.evidenceSlots, (row, taskId) => {
    return `evidence-slot / ${String(row.requirementCode ?? '—')} / ${taskId}`
  })
})

const slaRows = computed(() =>
  (slaPage.value?.items ?? []).map((item) => ({
    slaInstanceId: item.slaInstanceId,
    slaRef: item.slaRef,
    status: item.status,
    deadlineAt: item.deadlineAt,
    remainingSeconds: item.remainingSeconds,
    overdueSeconds: item.overdueSeconds,
    taskId: item.taskId,
  })),
)
const stageRows = computed(() =>
  (stages.value?.stages ?? []).map((item) => ({
    id: item.id,
    stageCode: item.stageCode,
    sequenceNo: item.sequenceNo,
    status: item.status,
    activatedAt: item.activatedAt,
    completedAt: item.completedAt,
  })),
)
const taskRows = computed(() =>
  (taskPage.value?.items ?? []).map((item) => ({
    id: item.id,
    taskType: item.taskType,
    taskKind: item.taskKind,
    status: item.status,
    stageCode: item.stageCode,
    priority: item.priority,
    version: item.version,
  })),
)
const timelineRows = computed(() =>
  (timelinePage.value?.items ?? []).map((item) => ({
    id: item.id,
    category: item.category,
    eventType: item.eventType,
    occurredAt: item.occurredAt,
    resourceType: item.resourceType,
    resourceId: item.resourceId,
    outcomeCode: item.outcomeCode,
  })),
)

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
  <section class="workspace">
    <header class="top">
      <div>
        <h2>工单工作区</h2>
        <p class="meta">{{ workOrderId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="loadWorkspace">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="workspace">
      <div class="grid">
        <article class="card">
          <h3>概览</h3>
          <dl>
            <div><dt>状态</dt><dd>{{ workspace.header.status }}</dd></div>
            <div>
              <dt>项目</dt>
              <dd>
                <RouterLink
                  :to="{
                    name: 'ADMIN.PROJECT.DETAIL',
                    params: { id: workspace.header.projectId },
                  }"
                >
                  {{ workspace.header.projectId }}
                </RouterLink>
              </dd>
            </div>
            <div><dt>外部单号</dt><dd>{{ workspace.header.externalOrderCode || '—' }}</dd></div>
            <div><dt>时间线 freshness</dt><dd>{{ workspace.timelineFreshnessStatus }}</dd></div>
            <div><dt>asOf</dt><dd>{{ workspace.meta.asOf }}</dd></div>
            <div><dt>allowed-actions</dt><dd>{{ workspace.allowedActionLink || '—' }}</dd></div>
          </dl>
        </article>

        <article class="card">
          <h3>当前任务 / 责任 / SLA / 异常</h3>
          <dl>
            <div>
              <dt>当前任务</dt>
              <dd v-if="workspace.currentTaskSummary">
                {{ workspace.currentTaskSummary.taskType }} /
                {{ workspace.currentTaskSummary.status }}
                <small>
                  <RouterLink
                    :to="{
                      name: 'ADMIN.TASK.DETAIL',
                      params: { id: workspace.currentTaskSummary.taskId },
                    }"
                  >
                    {{ workspace.currentTaskSummary.taskId }}
                  </RouterLink>
                </small>
              </dd>
              <dd v-else>—</dd>
            </div>
            <div>
              <dt>服务责任</dt>
              <dd v-if="workspace.serviceAssignmentSummary">
                network {{ String(workspace.serviceAssignmentSummary.networkId ?? '—') }} /
                tech {{ String(workspace.serviceAssignmentSummary.technicianId ?? '—') }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div class="manual-assign">
              <dt>人工初派</dt>
              <dd>
                <label>
                  networkAssigneeId
                  <input
                    v-model="networkAssigneeId"
                    aria-label="manual-assign networkAssigneeId"
                  />
                </label>
                <label>
                  technicianAssigneeId
                  <input
                    v-model="technicianAssigneeId"
                    aria-label="manual-assign technicianAssigneeId"
                  />
                </label>
                <label>
                  businessType
                  <input
                    v-model="assignBusinessType"
                    aria-label="manual-assign businessType"
                  />
                </label>
                <button
                  type="button"
                  :disabled="manualAssignBusy || !workspace.currentTaskSummary"
                  @click="runManualAssign"
                >
                  manual-assign
                </button>
                <p v-if="manualAssignError" class="error">{{ manualAssignError }}</p>
                <p v-if="manualAssignMessage" class="meta">{{ manualAssignMessage }}</p>
              </dd>
            </div>
            <div>
              <dt>SLA</dt>
              <dd v-if="workspace.slaSummary">
                open {{ Number(workspace.slaSummary.openCount ?? 0) }} /
                breached {{ Number(workspace.slaSummary.breachedCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div>
              <dt>异常</dt>
              <dd v-if="workspace.exceptionSummary">
                open {{ Number(workspace.exceptionSummary.openCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
          </dl>
        </article>

        <article class="card">
          <h3>最近活动</h3>
          <ul v-if="activity?.items?.length">
            <li v-for="(item, index) in activity.items" :key="index">
              <strong>{{ item.eventType || item.type || 'event' }}</strong>
              <span>{{ item.occurredAt || '—' }}</span>
              <small>{{ item.resourceType }} {{ item.resourceId }}</small>
            </li>
          </ul>
          <p v-else>暂无活动摘要</p>
        </article>

        <article class="card">
          <h3>当前任务命令</h3>
          <p class="meta">按钮仅来自服务端 allowed-actions；执行后重新拉取工作区。</p>
          <p v-if="allowedActionsError" class="error">{{ allowedActionsError }}</p>
          <p v-else-if="!workspace.currentTaskSummary">无当前任务</p>
          <TaskCommandPanel
            v-else-if="allowedActions"
            :task-id="workspace.currentTaskSummary.taskId"
            :allowed-actions="allowedActions"
            @executed="loadWorkspace"
          />
          <p v-else>暂无允许动作或无权读取</p>
          <ul v-if="allowedActions?.actions?.length" class="action-list">
            <li v-for="action in allowedActions.actions" :key="action.code">
              <strong>{{ action.label }}</strong>
              <span>{{ action.code }}</span>
              <small>
                obligations:
                {{ action.obligations.length ? action.obligations.join(', ') : 'none' }}
              </small>
            </li>
          </ul>
          <p v-if="allowedActions" class="meta">
            asOf {{ allowedActions.asOf }} / v{{ allowedActions.resourceVersion }}
          </p>
        </article>
      </div>

      <article v-if="workOrderDetail" class="card">
        <h3>工单权威事实</h3>
        <dl>
          <div><dt>status</dt><dd>{{ workOrderDetail.workOrder.status }}</dd></div>
          <div><dt>clientCode</dt><dd>{{ workOrderDetail.workOrder.clientCode }}</dd></div>
          <div><dt>brandCode</dt><dd>{{ workOrderDetail.workOrder.brandCode }}</dd></div>
          <div><dt>serviceProductCode</dt><dd>{{ workOrderDetail.workOrder.serviceProductCode }}</dd></div>
          <div><dt>externalOrderCode</dt><dd>{{ workOrderDetail.workOrder.externalOrderCode }}</dd></div>
          <div><dt>version</dt><dd>{{ workOrderDetail.workOrder.version }}</dd></div>
          <div><dt>asOf</dt><dd>{{ workOrderDetail.asOf }}</dd></div>
        </dl>
      </article>
      <p v-if="authorityError" class="error">{{ authorityError }}</p>

      <article v-if="stages" class="card">
        <h3>Workflow / Stage</h3>
        <p class="meta">
          workflow={{ stages.workflow?.workflowKey || '—' }} /
          {{ stages.workflow?.status || '未初始化' }} /
          asOf {{ stages.asOf }}
        </p>
      </article>
      <QueueTable
        title="Stage 投影"
        :columns="['id', 'stageCode', 'sequenceNo', 'status', 'activatedAt', 'completedAt']"
        :rows="stageRows"
        :loading="false"
        :error="null"
        :as-of="stages?.asOf"
        :next-cursor="null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />

      <QueueTable
        title="工单 Task 摘要"
        :columns="['id', 'taskType', 'taskKind', 'status', 'stageCode', 'priority', 'version']"
        :rows="taskRows"
        :loading="false"
        :error="null"
        :as-of="taskPage?.asOf"
        :next-cursor="taskPage?.nextCursor ?? null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />
      <p v-if="taskPage?.items?.length" class="links">
        打开任务：
        <RouterLink
          v-for="item in taskPage.items"
          :key="item.id"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.id } }"
        >
          {{ item.taskType }} / {{ item.id }}
        </RouterLink>
      </p>

      <QueueTable
        title="核心时间线"
        :columns="['id', 'category', 'eventType', 'occurredAt', 'resourceType', 'resourceId', 'outcomeCode']"
        :rows="timelineRows"
        :loading="false"
        :error="null"
        :as-of="timelinePage?.asOf"
        :next-cursor="timelinePage?.nextCursor ?? null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />
      <p v-if="timelinePage" class="meta">
        freshness={{ timelinePage.freshnessStatus }} / resourceVersion={{ timelinePage.resourceVersion }} /
        lastProjectedAt={{ timelinePage.lastProjectedAt || '—' }}
      </p>

      <QueueTable
        title="工单 SLA 实例"
        :columns="['slaInstanceId', 'slaRef', 'status', 'deadlineAt', 'remainingSeconds', 'overdueSeconds', 'taskId']"
        :rows="slaRows"
        :loading="false"
        :error="slaError"
        :as-of="slaPage?.asOf"
        :next-cursor="slaPage?.nextCursor ?? null"
        @refresh="loadSlaInstances"
        @next="() => undefined"
      />
      <p v-if="slaPage?.items?.length" class="links">
        打开 SLA 详情：
        <RouterLink
          v-for="item in slaPage.items"
          :key="item.slaInstanceId"
          :to="{ name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } }"
        >
          {{ item.slaRef || item.slaInstanceId }}
        </RouterLink>
      </p>
      <p v-if="slaPage?.items?.length" class="links sla-task-links">
        打开 SLA 关联任务：
        <RouterLink
          v-for="item in slaPage.items"
          :key="`sla-task-${item.slaInstanceId}`"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
        >
          <!-- 三段标签：避免与权威区「taskType / taskId」strict 冲突 -->
          SLA / {{ item.slaRef || item.slaInstanceId }} / {{ item.taskId }}
        </RouterLink>
      </p>

      <article class="card sections">
        <h3>按需区块</h3>
        <div class="tabs">
          <button
            v-for="code in sections"
            :key="code"
            type="button"
            :class="{ active: activeSection === code }"
            :disabled="workspace.sectionAvailability[code] === 'UNAVAILABLE'"
            @click="loadSection(code)"
          >
            {{ code }}
            <em>{{ workspace.sectionAvailability[code] || '?' }}</em>
          </button>
        </div>
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <template v-else>
          <p v-if="inboundEnvelopeLinks.length" class="links inbound-links">
            打开入站 Envelope：
            <RouterLink
              v-for="item in inboundEnvelopeLinks"
              :key="item.inboundEnvelopeId"
              :to="{
                name: 'ADMIN.INTEGRATION.INBOUND.DETAIL',
                params: { id: item.inboundEnvelopeId },
              }"
            >
              {{ item.messageType }} / {{ item.processingStatus }}
              <template v-if="item.resultCode"> / {{ item.resultCode }}</template>
            </RouterLink>
          </p>
          <p v-if="outboundDeliveryLinks.length" class="links outbound-links">
            打开外发交付：
            <RouterLink
              v-for="item in outboundDeliveryLinks"
              :key="item.deliveryId"
              :to="{
                name: 'ADMIN.INTEGRATION.DETAIL',
                params: { id: item.deliveryId },
              }"
            >
              {{ item.businessMessageType }} / {{ item.status }} / {{ item.externalOrderCode }}
            </RouterLink>
          </p>
          <p v-if="reviewCaseLinks.length" class="links review-links">
            打开审核案例：
            <RouterLink
              v-for="item in reviewCaseLinks"
              :key="item.reviewCaseId"
              :to="{
                name: 'ADMIN.REVIEW.DETAIL',
                params: { id: item.reviewCaseId },
              }"
            >
              {{ item.origin }} / {{ item.status }} / {{ item.reviewCaseId }}
            </RouterLink>
          </p>
          <p v-if="correctionCaseLinks.length" class="links correction-links">
            打开整改案例：
            <RouterLink
              v-for="item in correctionCaseLinks"
              :key="item.correctionCaseId"
              :to="{
                name: 'ADMIN.CORRECTION.DETAIL',
                params: { id: item.correctionCaseId },
              }"
            >
              {{ item.status }} / {{ item.correctionCaseId }}
            </RouterLink>
          </p>
          <p v-if="taskSectionLinks.length" class="links task-section-links">
            打开区块任务：
            <RouterLink
              v-for="item in taskSectionLinks"
              :key="item.taskId"
              :to="{
                name: 'ADMIN.TASK.DETAIL',
                params: { id: item.taskId },
              }"
            >
              {{ item.taskType }} / {{ item.taskKind }} / {{ item.status }} / {{ item.taskId }}
            </RouterLink>
          </p>
          <p v-if="timelineResourceLinks.length" class="links timeline-resource-links">
            打开时间线资源：
            <RouterLink
              v-for="item in timelineResourceLinks"
              :key="item.key"
              :to="{
                name: item.routeName,
                params: { id: item.resourceId },
              }"
            >
              {{ item.label }}
            </RouterLink>
          </p>
          <p v-if="appointmentDetailLinks.length" class="links appointment-detail-links">
            打开预约详情：
            <RouterLink
              v-for="item in appointmentDetailLinks"
              :key="item.appointmentId"
              :to="{
                name: 'ADMIN.APPOINTMENT.DETAIL',
                params: { id: item.appointmentId },
              }"
            >
              {{ item.type }} / {{ item.status }} / {{ item.appointmentId }}
            </RouterLink>
          </p>
          <p v-if="visitDetailLinks.length" class="links visit-detail-links">
            打开上门详情：
            <RouterLink
              v-for="item in visitDetailLinks"
              :key="item.visitId"
              :to="{
                name: 'ADMIN.VISIT.DETAIL',
                params: { id: item.visitId },
              }"
            >
              {{ item.status }} / seq={{ item.visitSequence }} / {{ item.visitId }}
            </RouterLink>
          </p>
          <p v-if="contactAttemptDetailLinks.length" class="links contact-attempt-detail-links">
            打开联系详情：
            <RouterLink
              v-for="item in contactAttemptDetailLinks"
              :key="item.contactAttemptId"
              :to="{
                name: 'ADMIN.CONTACT_ATTEMPT.DETAIL',
                params: { id: item.contactAttemptId },
              }"
            >
              {{ item.channel }} / {{ item.resultCode }} / {{ item.contactAttemptId }}
            </RouterLink>
          </p>
          <p v-if="appointmentVisitTaskLinks.length" class="links appointment-visit-task-links">
            打开预约上门关联任务：
            <RouterLink
              v-for="item in appointmentVisitTaskLinks"
              :key="item.key"
              :to="{
                name: 'ADMIN.TASK.DETAIL',
                params: { id: item.taskId },
              }"
            >
              {{ item.label }}
            </RouterLink>
          </p>
          <p v-if="formSubmissionDetailLinks.length" class="links form-submission-detail-links">
            打开表单提交详情：
            <RouterLink
              v-for="item in formSubmissionDetailLinks"
              :key="item.submissionId"
              :to="{
                name: 'ADMIN.FORM_SUBMISSION.DETAIL',
                params: { id: item.submissionId },
              }"
            >
              {{ item.formKey }} / {{ item.validationStatus }} / {{ item.submissionId }}
            </RouterLink>
          </p>
          <p v-if="evidenceItemDetailLinks.length" class="links evidence-item-detail-links">
            打开资料项详情：
            <RouterLink
              v-for="item in evidenceItemDetailLinks"
              :key="item.evidenceItemId"
              :to="{
                name: 'ADMIN.EVIDENCE_ITEM.DETAIL',
                params: { id: item.evidenceItemId },
              }"
            >
              #{{ item.itemOrdinal }} / {{ item.status }} / {{ item.evidenceItemId }}
            </RouterLink>
          </p>
          <p v-if="formsEvidenceTaskLinks.length" class="links forms-evidence-task-links">
            打开表单资料关联任务：
            <RouterLink
              v-for="item in formsEvidenceTaskLinks"
              :key="item.key"
              :to="{
                name: 'ADMIN.TASK.DETAIL',
                params: { id: item.taskId },
              }"
            >
              {{ item.label }}
            </RouterLink>
          </p>
          <pre>{{ sectionPreview }}</pre>
        </template>
      </article>
    </template>
  </section>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
h2,
h3 {
  margin: 0 0 0.75rem;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.55rem;
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
}
dd small {
  display: block;
  color: #829ab1;
  font-family: ui-monospace, monospace;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}
li {
  display: grid;
  gap: 0.15rem;
}
li span,
li small {
  color: #627d98;
  font-size: 0.85rem;
}
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
}
.tabs button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 999px;
  padding: 0.35rem 0.7rem;
  cursor: pointer;
}
.tabs button.active {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
}
.tabs button em {
  font-style: normal;
  margin-left: 0.35rem;
  opacity: 0.75;
  font-size: 0.75rem;
}
.tabs button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
pre {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.8rem;
}
.error {
  color: #9b1c1c;
}
.action-list {
  list-style: none;
  margin: 0.75rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.35rem;
}
.action-list small {
  display: block;
  color: #829ab1;
}
.links {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
.manual-assign dd {
  display: grid;
  gap: 0.4rem;
}
.manual-assign label {
  display: grid;
  gap: 0.15rem;
  font-size: 0.8rem;
  color: #627d98;
}
.manual-assign input {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.35rem 0.5rem;
  font-family: ui-monospace, monospace;
}
</style>
