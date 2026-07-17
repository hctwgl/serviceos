<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  assignNetworkPortalTechnician,
  beginNetworkPortalEvidenceUploadOnBehalf,
  cancelNetworkPortalAppointment,
  confirmNetworkPortalAppointment,
  finalizeNetworkPortalEvidenceUploadOnBehalf,
  listNetworkPortalTaskAppointments,
  listNetworkPortalTaskContactAttempts,
  listNetworkPortalTasks,
  listNetworkPortalTechnicians,
  markNetworkPortalAppointmentNoShow,
  proposeNetworkPortalAppointment,
  recordNetworkPortalTaskContactAttempt,
  reassignNetworkPortalTechnician,
  rescheduleNetworkPortalAppointment,
  resubmitNetworkPortalCorrectionCase,
  type NetworkPortalAppointment,
  type NetworkPortalContactAttempt,
  type NetworkPortalTaskItem,
  type NetworkPortalTechnicianItem,
  type NetworkPortalDirectorySlaRiskSummary,
  type NetworkPortalWorkspaceAppointmentSummary,
  type NetworkPortalWorkspaceContactAttemptSummary,
  type NetworkPortalWorkspaceCorrectionCaseSummary,
  type NetworkPortalWorkspaceEvidenceItemSummary,
  type NetworkPortalWorkspaceEvidenceSlotSummary,
} from '../api/networkPortal'
import { getMe } from '../api/me'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const queryTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<NetworkPortalTaskItem[]>([])
const technicians = ref<NetworkPortalTechnicianItem[]>([])
const directoryTechnicians = ref<NetworkPortalTechnicianItem[]>([])
const directoryAppointments = ref<NetworkPortalWorkspaceAppointmentSummary[] | null>(null)
const directoryContactAttempts = ref<NetworkPortalWorkspaceContactAttemptSummary[] | null>(null)
const directoryCorrections = ref<NetworkPortalWorkspaceCorrectionCaseSummary[] | null>(null)
const directoryEvidenceSlots = ref<NetworkPortalWorkspaceEvidenceSlotSummary[] | null>(null)
const directoryEvidenceItems = ref<NetworkPortalWorkspaceEvidenceItemSummary[] | null>(null)
const directorySlaRiskSummaries = ref<NetworkPortalDirectorySlaRiskSummary[] | null>(null)
const appointments = ref<NetworkPortalAppointment[]>([])
const contactAttempts = ref<NetworkPortalContactAttempt[]>([])
const error = ref<string | null>(null)

function technicianLabel(technicianId: string | null | undefined) {
  if (!technicianId) {
    return '未指派'
  }
  // M230：优先目录页服务端旁载；回退指派候选列表（M217 fan-in / 写控件全集）
  const fromDirectory = directoryTechnicians.value.find(
    (item) => item.technicianProfileId === technicianId,
  )
  if (fromDirectory) {
    return fromDirectory.displayName
  }
  const tech = technicians.value.find((item) => item.technicianProfileId === technicianId)
  return tech ? tech.displayName : technicianId
}

function directoryAppointmentWindowLabel(taskId: string) {
  if (directoryAppointments.value === null) {
    return '—'
  }
  const matched = directoryAppointments.value.filter((apt) => apt.taskId === taskId)
  if (matched.length === 0) {
    return '暂无'
  }
  const first = matched[0]
  if (!first.windowStart && !first.windowEnd) {
    return first.status
  }
  return `${first.windowStart ?? '?'} → ${first.windowEnd ?? '?'}（${first.status}）`
}

function directoryContactLabel(taskId: string) {
  if (directoryContactAttempts.value === null) {
    return '—'
  }
  const matched = directoryContactAttempts.value.filter((row) => row.taskId === taskId)
  if (matched.length === 0) {
    return '暂无'
  }
  const first = matched[0]
  return `${first.channel} · ${first.resultCode} · ${first.startedAt}`
}

function directoryCorrectionLabel(taskId: string) {
  if (directoryCorrections.value === null) {
    return '—'
  }
  const matched = directoryCorrections.value.filter((row) => row.taskId === taskId)
  if (matched.length === 0) {
    return '暂无'
  }
  const openCount = matched.filter((row) => row.status === 'OPEN').length
  const first = matched[0]
  if (openCount > 0) {
    return `OPEN ×${openCount} · ${first.reasonCodes.join(',') || first.status}`
  }
  return `${first.status} · ${first.reasonCodes.join(',') || first.correctionCaseId}`
}

function directoryEvidenceLabel(taskId: string) {
  if (directoryEvidenceSlots.value === null) {
    return '—'
  }
  const slots = directoryEvidenceSlots.value.filter((row) => row.taskId === taskId)
  const items = (directoryEvidenceItems.value ?? []).filter((row) => row.taskId === taskId)
  if (slots.length === 0 && items.length === 0) {
    return '暂无'
  }
  const missing = slots.filter((row) => row.status === 'MISSING').length
  const openItems = items.filter((row) => row.status === 'OPEN').length
  const parts: string[] = []
  if (missing > 0) {
    parts.push(`MISSING ×${missing}`)
  }
  if (openItems > 0) {
    parts.push(`OPEN项 ×${openItems}`)
  }
  if (parts.length === 0) {
    const first = slots[0]
    return first ? `${first.status} · ${first.requirementCode}` : `项 ×${items.length}`
  }
  return parts.join(' · ')
}

function directorySlaRiskLabel(taskId: string) {
  if (directorySlaRiskSummaries.value === null) {
    return '—'
  }
  const matched = directorySlaRiskSummaries.value.find((row) => row.taskId === taskId)
  if (!matched) {
    return '暂无'
  }
  return `开放 ${matched.openCount} / 超时 ${matched.breachedCount}`
}

function taskRegionLabel(item: NetworkPortalTaskItem) {
  const parts = [item.provinceCode, item.cityCode, item.districtCode].filter(Boolean)
  return parts.length ? parts.join('/') : '—'
}
const selectedTaskId = ref('')
const selectedTechnicianId = ref('')
const businessType = ref('INSTALLATION')
const assignBusy = ref(false)
const assignError = ref<string | null>(null)
const assignMessage = ref<string | null>(null)
const reassignReason = ref('MANUAL_REASSIGNMENT')
const reassignBusy = ref(false)
const reassignError = ref<string | null>(null)
const reassignMessage = ref<string | null>(null)

const appointmentType = ref('SURVEY')
const addressRef = ref('network-portal-address')
const addressVersion = ref('v1')
const windowStart = ref('2026-08-20T01:00:00.000Z')
const windowEnd = ref('2026-08-20T04:00:00.000Z')
const appointmentBusy = ref(false)
const appointmentError = ref<string | null>(null)
const appointmentMessage = ref<string | null>(null)
const rescheduleReason = ref('CUSTOMER_REQUESTED_LATER')
const cancelReason = ref('CUSTOMER_CANCELLED')
const rescheduleStart = ref('2026-09-11T02:00:00.000Z')
const rescheduleEnd = ref('2026-09-11T05:00:00.000Z')
const noShowReason = ref('CUSTOMER_ABSENT')
const noShowPartyRef = ref('customer-ref')
const noShowEvidenceRef = ref('file-ref-1')
const contactChannel = ref('PHONE')
const contactPartyRef = ref('party-ref')
const contactResultCode = ref('NO_ANSWER')
const contactNote = ref('网点联系')
const contactStart = ref('2026-07-17T08:00:00.000Z')
const contactEnd = ref('2026-07-17T08:05:00.000Z')
const contactBusy = ref(false)
const contactError = ref<string | null>(null)
const contactMessage = ref<string | null>(null)

const evidenceSlotId = ref('')
const onBehalfOf = ref('')
const onBehalfReason = ref('整改代补')
const evidenceSha256 = ref('a'.repeat(64))
const evidenceFileName = ref('site.png')
const evidenceExpectedSize = ref(128)
const uploadSessionId = ref('')
const evidenceBusy = ref(false)
const evidenceError = ref<string | null>(null)
const evidenceMessage = ref<string | null>(null)
const correctionCaseId = ref('')
const evidenceSetSnapshotId = ref('')
const resubmitBusy = ref(false)
const resubmitError = ref<string | null>(null)
const resubmitMessage = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    technicians.value = []
    directoryTechnicians.value = []
    directoryAppointments.value = null
    directoryContactAttempts.value = null
    directoryCorrections.value = null
    directoryEvidenceSlots.value = null
    directoryEvidenceItems.value = null
    directorySlaRiskSummaries.value = null
    appointments.value = []
    contactAttempts.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  let serverDirectoryTechnicians: NetworkPortalTechnicianItem[] | null = null
  try {
    const taskPage = await listNetworkPortalTasks(props.networkContextId)
    items.value = taskPage.items
    directoryAppointments.value =
      taskPage.appointments !== undefined ? taskPage.appointments : null
    directoryContactAttempts.value =
      taskPage.contactAttempts !== undefined ? taskPage.contactAttempts : null
    directoryCorrections.value =
      taskPage.corrections !== undefined ? taskPage.corrections : null
    directoryEvidenceSlots.value =
      taskPage.evidenceSlots !== undefined ? taskPage.evidenceSlots : null
    directoryEvidenceItems.value =
      taskPage.evidenceItems !== undefined ? taskPage.evidenceItems : null
    directorySlaRiskSummaries.value =
      taskPage.slaRiskSummaries !== undefined ? taskPage.slaRiskSummaries : null
    if (taskPage.technicians !== undefined) {
      serverDirectoryTechnicians = taskPage.technicians
      directoryTechnicians.value = taskPage.technicians
    } else {
      directoryTechnicians.value = []
    }
    error.value = null
    const fromQuery = queryTaskId.value
      ? items.value.find((item) => item.taskId === queryTaskId.value)
      : null
    if (fromQuery) {
      selectedTaskId.value = fromQuery.taskId
      if (fromQuery.businessType) {
        businessType.value = fromQuery.businessType
      }
    } else if (!selectedTaskId.value && items.value.length > 0) {
      selectedTaskId.value = items.value[0].taskId
      if (items.value[0].businessType) {
        businessType.value = items.value[0].businessType
      }
    } else if (
      selectedTaskId.value
      && !items.value.some((item) => item.taskId === selectedTaskId.value)
      && items.value.length > 0
    ) {
      selectedTaskId.value = items.value[0].taskId
    }
  } catch (err) {
    items.value = []
    directoryTechnicians.value = []
    directoryAppointments.value = null
    directoryContactAttempts.value = null
    directoryCorrections.value = null
    directoryEvidenceSlots.value = null
    directoryEvidenceItems.value = null
    directorySlaRiskSummaries.value = null
    error.value = err instanceof Error ? err.message : '任务列表加载失败'
  }
  try {
    // 指派/改派下拉仍需 ACTIVE 候选全集；目录展示优先用 page.technicians
    const techPage = await listNetworkPortalTechnicians(props.networkContextId)
    technicians.value = techPage.items
    if (serverDirectoryTechnicians === null) {
      directoryTechnicians.value = techPage.items
    }
    if (!selectedTechnicianId.value && technicians.value.length > 0) {
      selectedTechnicianId.value = technicians.value[0].technicianProfileId
    }
  } catch {
    technicians.value = []
  }
  await loadAppointments()
  await loadContactAttempts()
}

async function loadAppointments() {
  appointments.value = []
  if (!props.networkContextId || !selectedTaskId.value) {
    return
  }
  try {
    const page = await listNetworkPortalTaskAppointments(
      props.networkContextId,
      selectedTaskId.value,
    )
    appointments.value = page
  } catch {
    appointments.value = []
  }
}

async function loadContactAttempts() {
  contactAttempts.value = []
  if (!props.networkContextId || !selectedTaskId.value) {
    return
  }
  try {
    contactAttempts.value = await listNetworkPortalTaskContactAttempts(
      props.networkContextId,
      selectedTaskId.value,
    )
  } catch {
    contactAttempts.value = []
  }
}

async function submitAssign() {
  if (!props.networkContextId) {
    assignError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value || !selectedTechnicianId.value) {
    assignError.value = '请选择任务与师傅'
    return
  }
  assignBusy.value = true
  assignError.value = null
  assignMessage.value = null
  try {
    const result = await assignNetworkPortalTechnician(props.networkContextId, selectedTaskId.value, {
      technicianAssigneeId: selectedTechnicianId.value,
      businessType: businessType.value.trim() || 'INSTALLATION',
    })
    assignMessage.value =
      `已指派 network=${result.data.networkAssigneeId} tech=${result.data.technicianAssigneeId}`
    await load()
  } catch (err) {
    assignError.value = err instanceof Error ? err.message : '指派师傅失败'
  } finally {
    assignBusy.value = false
  }
}

async function submitEvidenceBegin() {
  if (!props.networkContextId) {
    evidenceError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value || !evidenceSlotId.value || !onBehalfOf.value) {
    evidenceError.value = '请填写任务、槽位与 onBehalfOf'
    return
  }
  evidenceBusy.value = true
  evidenceError.value = null
  evidenceMessage.value = null
  try {
    const result = await beginNetworkPortalEvidenceUploadOnBehalf(
      props.networkContextId,
      selectedTaskId.value,
      evidenceSlotId.value,
      {
        originalFileName: evidenceFileName.value.trim() || 'site.png',
        declaredMimeType: 'image/png',
        expectedSize: Number(evidenceExpectedSize.value) || 128,
        expectedSha256: evidenceSha256.value.trim(),
        captureMetadata: {
          captureSource: 'CAMERA',
          capturedAt: new Date().toISOString(),
        },
        onBehalfOf: onBehalfOf.value.trim(),
        onBehalfReason: onBehalfReason.value.trim() || '整改代补',
      },
    )
    uploadSessionId.value = result.data.uploadSessionId
    evidenceMessage.value = `已创建上传会话 ${result.data.uploadSessionId}`
  } catch (err) {
    evidenceError.value = err instanceof Error ? err.message : '代补 Begin 失败'
  } finally {
    evidenceBusy.value = false
  }
}

async function submitEvidenceFinalize() {
  if (!props.networkContextId) {
    evidenceError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value || !evidenceSlotId.value || !uploadSessionId.value) {
    evidenceError.value = '请先 Begin 并填写槽位/会话'
    return
  }
  evidenceBusy.value = true
  evidenceError.value = null
  evidenceMessage.value = null
  try {
    const result = await finalizeNetworkPortalEvidenceUploadOnBehalf(
      props.networkContextId,
      selectedTaskId.value,
      evidenceSlotId.value,
      uploadSessionId.value,
      {
        actualSha256: evidenceSha256.value.trim(),
        finalizeCommandId: crypto.randomUUID(),
      },
    )
    evidenceMessage.value = `已 Finalize evidenceItem=${result.data.evidenceItemId}`
  } catch (err) {
    evidenceError.value = err instanceof Error ? err.message : '代补 Finalize 失败'
  } finally {
    evidenceBusy.value = false
  }
}

async function submitCorrectionResubmit() {
  if (!props.networkContextId) {
    resubmitError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!correctionCaseId.value || !evidenceSetSnapshotId.value) {
    resubmitError.value = '请填写 correctionCaseId 与 snapshotId'
    return
  }
  resubmitBusy.value = true
  resubmitError.value = null
  resubmitMessage.value = null
  try {
    const result = await resubmitNetworkPortalCorrectionCase(
      props.networkContextId,
      correctionCaseId.value.trim(),
      { evidenceSetSnapshotId: evidenceSetSnapshotId.value.trim() },
    )
    resubmitMessage.value = `整改已补传 status=${result.data.status}`
  } catch (err) {
    resubmitError.value = err instanceof Error ? err.message : '整改 resubmit 失败'
  } finally {
    resubmitBusy.value = false
  }
}

async function submitReassign() {
  if (!props.networkContextId) {
    reassignError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value || !selectedTechnicianId.value) {
    reassignError.value = '请选择任务与目标师傅'
    return
  }
  reassignBusy.value = true
  reassignError.value = null
  reassignMessage.value = null
  try {
    const result = await reassignNetworkPortalTechnician(
      props.networkContextId,
      selectedTaskId.value,
      {
        technicianAssigneeId: selectedTechnicianId.value,
        businessType: businessType.value.trim() || 'INSTALLATION',
        reasonCode: reassignReason.value.trim() || 'MANUAL_REASSIGNMENT',
      },
    )
    reassignMessage.value =
      `已改派 network=${result.data.networkAssigneeId} tech=${result.data.technicianAssigneeId}`
    await load()
  } catch (err) {
    reassignError.value = err instanceof Error ? err.message : '改派师傅失败'
  } finally {
    reassignBusy.value = false
  }
}

async function submitPropose() {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value) {
    appointmentError.value = '请选择任务'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await proposeNetworkPortalAppointment(
      props.networkContextId,
      selectedTaskId.value,
      {
        type: appointmentType.value,
        window: {
          start: windowStart.value,
          end: windowEnd.value,
          timezone: 'Asia/Shanghai',
          estimatedDurationMinutes: 120,
        },
        addressRef: addressRef.value.trim() || 'network-portal-address',
        addressVersion: addressVersion.value.trim() || 'v1',
      },
    )
    appointmentMessage.value =
      `已提议 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '提议预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitConfirm(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const me = await getMe()
    const result = await confirmNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        confirmedPartyType: 'NETWORK_MEMBER',
        confirmedPartyRef: me.principalId,
        confirmationChannel: 'PHONE',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已确认 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '确认预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitReschedule(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await rescheduleNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        newWindow: {
          start: rescheduleStart.value,
          end: rescheduleEnd.value,
          timezone: 'Asia/Shanghai',
          estimatedDurationMinutes: 120,
        },
        reasonCode: rescheduleReason.value.trim() || 'CUSTOMER_REQUESTED_LATER',
        note: 'Network Portal 改约',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已改约 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '改约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitCancel(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await cancelNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        reasonCode: cancelReason.value.trim() || 'CUSTOMER_CANCELLED',
        note: 'Network Portal 取消',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已取消 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '取消预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitNoShow(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await markNetworkPortalAppointmentNoShow(
      props.networkContextId,
      item.appointmentId,
      {
        noShowPartyType: 'CUSTOMER',
        noShowPartyRef: noShowPartyRef.value.trim() || 'customer-ref',
        reasonCode: noShowReason.value.trim() || 'CUSTOMER_ABSENT',
        evidenceRefs: [noShowEvidenceRef.value.trim() || 'file-ref-1'],
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已标记爽约 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '标记爽约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitContactAttempt() {
  if (!props.networkContextId) {
    contactError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value) {
    contactError.value = '请选择任务'
    return
  }
  contactBusy.value = true
  contactError.value = null
  contactMessage.value = null
  try {
    const result = await recordNetworkPortalTaskContactAttempt(
      props.networkContextId,
      selectedTaskId.value,
      {
        channel: contactChannel.value.trim() || 'PHONE',
        contactedPartyRef: contactPartyRef.value.trim() || 'party-ref',
        startedAt: contactStart.value,
        endedAt: contactEnd.value,
        resultCode: contactResultCode.value,
        note: contactNote.value.trim() || null,
      },
    )
    contactMessage.value =
      `已记录联系 contactAttempt=${result.data.contactAttemptId} result=${result.data.resultCode}`
    await loadContactAttempts()
  } catch (err) {
    contactError.value = err instanceof Error ? err.message : '记录联系失败'
  } finally {
    contactBusy.value = false
  }
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  selectedTaskId.value = ''
  selectedTechnicianId.value = ''
  void load()
})
watch(queryTaskId, () => {
  void load()
})
watch(selectedTaskId, () => {
  void loadAppointments()
  void loadContactAttempts()
})
</script>

<template>
  <section data-testid="network-portal-tasks" data-page-id="NETWORK.TASK.QUEUE">
    <h2>本网点任务</h2>
    <p
      v-if="queryTaskId"
      class="filter"
      data-testid="tasks-task-filter"
    >
      已按 query 选中 taskId：{{ selectedTaskId || queryTaskId }}
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-tasks-table">
      <thead>
        <tr>
          <th>任务</th>
          <th>工单</th>
          <th>项目</th>
          <th>服务产品</th>
          <th>区域</th>
          <th>状态</th>
          <th>阶段</th>
          <th>类型</th>
          <th>种类</th>
          <th>业务</th>
          <th>接收时间</th>
          <th>生效自</th>
          <th>师傅</th>
          <th v-if="directoryAppointments !== null">预约窗口</th>
          <th v-if="directoryContactAttempts !== null">最近联系</th>
          <th v-if="directoryEvidenceSlots !== null">资料</th>
          <th v-if="directoryCorrections !== null">整改</th>
          <th v-if="directorySlaRiskSummaries !== null">SLA 风险</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.taskId" :data-testid="`task-row-${item.taskId}`">
          <td>{{ item.taskId }}</td>
          <td>
            <RouterLink
              :to="`/network-portal/work-orders/${item.workOrderId}`"
              data-testid="task-work-order-workspace-deeplink"
            >
              {{ item.workOrderId }}
            </RouterLink>
          </td>
          <td data-testid="task-project-id">{{ item.projectId ?? '—' }}</td>
          <td data-testid="task-service-product">{{ item.serviceProductCode ?? '—' }}</td>
          <td data-testid="task-region">{{ taskRegionLabel(item) }}</td>
          <td>{{ item.status ?? '—' }}</td>
          <td data-testid="task-stage-code">{{ item.stageCode ?? '—' }}</td>
          <td>{{ item.taskType ?? '—' }}</td>
          <td data-testid="task-kind">{{ item.taskKind ?? '—' }}</td>
          <td data-testid="task-business-type">{{ item.businessType ?? '—' }}</td>
          <td data-testid="task-received-at">{{ item.receivedAt ?? '—' }}</td>
          <td data-testid="task-effective-from">{{ item.effectiveFrom ?? '—' }}</td>
          <td data-testid="task-technician-label">{{ technicianLabel(item.technicianId) }}</td>
          <td
            v-if="directoryAppointments !== null"
            data-testid="task-appointment-window"
          >
            {{ directoryAppointmentWindowLabel(item.taskId) }}
          </td>
          <td
            v-if="directoryContactAttempts !== null"
            data-testid="task-contact-attempt"
          >
            {{ directoryContactLabel(item.taskId) }}
          </td>
          <td
            v-if="directoryEvidenceSlots !== null"
            data-testid="task-evidence-summary"
          >
            {{ directoryEvidenceLabel(item.taskId) }}
          </td>
          <td
            v-if="directoryCorrections !== null"
            data-testid="task-correction-summary"
          >
            {{ directoryCorrectionLabel(item.taskId) }}
          </td>
          <td
            v-if="directorySlaRiskSummaries !== null"
            data-testid="task-sla-risk"
          >
            {{ directorySlaRiskLabel(item.taskId) }}
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任任务</p>

    <form
      class="assign"
      data-testid="network-assign-technician-form"
      data-page-id="NETWORK.TECHNICIAN.ASSIGN"
      @submit.prevent="submitAssign"
    >
      <h3>指派师傅</h3>
      <p class="hint">调用 Network Portal 写命令；networkAssigneeId 由服务端强制为当前上下文网点。</p>
      <label>
        任务
        <select v-model="selectedTaskId" data-testid="assign-task-select" aria-label="assign task">
          <option disabled value="">选择任务</option>
          <option v-for="item in items" :key="item.taskId" :value="item.taskId">
            {{ item.taskId }}
          </option>
        </select>
      </label>
      <label>
        师傅
        <select
          v-model="selectedTechnicianId"
          data-testid="assign-technician-select"
          aria-label="assign technician"
        >
          <option disabled value="">选择师傅</option>
          <option
            v-for="tech in technicians"
            :key="tech.technicianProfileId"
            :value="tech.technicianProfileId"
          >
            {{ tech.displayName }}
          </option>
        </select>
      </label>
      <label>
        业务类型
        <input v-model="businessType" data-testid="assign-business-type" aria-label="business type" />
      </label>
      <label>
        改派原因
        <input
          v-model="reassignReason"
          data-testid="reassign-reason"
          aria-label="reassign reason"
        />
      </label>
      <button
        type="submit"
        data-testid="assign-technician-submit"
        :disabled="assignBusy || !props.networkContextId"
      >
        指派师傅
      </button>
      <button
        type="button"
        data-testid="reassign-technician-submit"
        :disabled="reassignBusy || !props.networkContextId"
        @click="submitReassign"
      >
        改派师傅
      </button>
      <p v-if="assignError" class="error" data-testid="assign-technician-error">{{ assignError }}</p>
      <p v-if="assignMessage" class="ok" data-testid="assign-technician-message">{{ assignMessage }}</p>
      <p v-if="reassignError" class="error" data-testid="reassign-technician-error">{{ reassignError }}</p>
      <p v-if="reassignMessage" class="ok" data-testid="reassign-technician-message">{{ reassignMessage }}</p>
    </form>

    <form
      class="assign"
      data-testid="network-appointment-form"
      data-page-id="NETWORK.APPOINTMENT"
      @submit.prevent="submitPropose"
    >
      <h3>本网点预约</h3>
      <p class="hint">
        调用 Network Portal 预约 propose/confirm/reschedule/cancel/mark-no-show 与联系尝试；
        确认方固定为 NETWORK_MEMBER + 当前主体；改约/取消/爽约使用列表 If-Match 版本。
      </p>
      <label>
        任务
        <select
          v-model="selectedTaskId"
          data-testid="appointment-task-select"
          aria-label="appointment task"
        >
          <option disabled value="">选择任务</option>
          <option v-for="item in items" :key="item.taskId" :value="item.taskId">
            {{ item.taskId }}
          </option>
        </select>
      </label>
      <label>
        类型
        <select v-model="appointmentType" data-testid="appointment-type" aria-label="appointment type">
          <option value="SURVEY">SURVEY</option>
          <option value="INSTALLATION">INSTALLATION</option>
          <option value="REPAIR">REPAIR</option>
          <option value="CORRECTION">CORRECTION</option>
          <option value="SECOND_VISIT">SECOND_VISIT</option>
        </select>
      </label>
      <label>
        窗口开始
        <input v-model="windowStart" data-testid="appointment-window-start" aria-label="window start" />
      </label>
      <label>
        窗口结束
        <input v-model="windowEnd" data-testid="appointment-window-end" aria-label="window end" />
      </label>
      <label>
        地址引用
        <input v-model="addressRef" data-testid="appointment-address-ref" aria-label="address ref" />
      </label>
      <label>
        地址版本
        <input
          v-model="addressVersion"
          data-testid="appointment-address-version"
          aria-label="address version"
        />
      </label>
      <label>
        改约窗口开始
        <input
          v-model="rescheduleStart"
          data-testid="appointment-reschedule-start"
          aria-label="reschedule window start"
        />
      </label>
      <label>
        改约窗口结束
        <input
          v-model="rescheduleEnd"
          data-testid="appointment-reschedule-end"
          aria-label="reschedule window end"
        />
      </label>
      <label>
        改约原因
        <input
          v-model="rescheduleReason"
          data-testid="appointment-reschedule-reason"
          aria-label="reschedule reason"
        />
      </label>
      <label>
        取消原因
        <input
          v-model="cancelReason"
          data-testid="appointment-cancel-reason"
          aria-label="cancel reason"
        />
      </label>
      <label>
        爽约原因
        <input
          v-model="noShowReason"
          data-testid="appointment-noshow-reason"
          aria-label="no-show reason"
        />
      </label>
      <label>
        爽约对象引用
        <input
          v-model="noShowPartyRef"
          data-testid="appointment-noshow-party-ref"
          aria-label="no-show party ref"
        />
      </label>
      <label>
        爽约证据引用
        <input
          v-model="noShowEvidenceRef"
          data-testid="appointment-noshow-evidence-ref"
          aria-label="no-show evidence ref"
        />
      </label>
      <button
        type="submit"
        data-testid="appointment-propose-submit"
        :disabled="appointmentBusy || !props.networkContextId"
      >
        提议预约
      </button>
      <ul data-testid="network-appointments-list" class="appointments">
        <li v-for="item in appointments" :key="item.appointmentId">
          <span>
            {{ item.appointmentId }} · {{ item.status }} · v{{ item.aggregateVersion }}
          </span>
          <span class="actions">
            <button
              v-if="item.status === 'PROPOSED'"
              type="button"
              data-testid="appointment-confirm-submit"
              :disabled="appointmentBusy"
              @click="submitConfirm(item)"
            >
              确认
            </button>
            <button
              v-if="item.status === 'CONFIRMED'"
              type="button"
              data-testid="appointment-reschedule-submit"
              :disabled="appointmentBusy"
              @click="submitReschedule(item)"
            >
              改约
            </button>
            <button
              v-if="item.status === 'PROPOSED' || item.status === 'CONFIRMED'"
              type="button"
              data-testid="appointment-cancel-submit"
              :disabled="appointmentBusy"
              @click="submitCancel(item)"
            >
              取消
            </button>
            <button
              v-if="item.status === 'CONFIRMED'"
              type="button"
              data-testid="appointment-noshow-submit"
              :disabled="appointmentBusy"
              @click="submitNoShow(item)"
            >
              标记爽约
            </button>
          </span>
        </li>
      </ul>
      <p v-if="appointmentError" class="error" data-testid="appointment-error">
        {{ appointmentError }}
      </p>
      <p v-if="appointmentMessage" class="ok" data-testid="appointment-message">
        {{ appointmentMessage }}
      </p>
    </form>

    <form
      class="assign"
      data-testid="network-contact-form"
      data-page-id="NETWORK.CONTACT"
      @submit.prevent="submitContactAttempt"
    >
      <h3>本网点联系尝试</h3>
      <p class="hint">调用 Network Portal ContactAttempt；操作者来自 JWT。</p>
      <label>
        渠道
        <input v-model="contactChannel" data-testid="contact-channel" aria-label="contact channel" />
      </label>
      <label>
        联系对象引用
        <input
          v-model="contactPartyRef"
          data-testid="contact-party-ref"
          aria-label="contact party ref"
        />
      </label>
      <label>
        结果码
        <select
          v-model="contactResultCode"
          data-testid="contact-result-code"
          aria-label="contact result code"
        >
          <option value="CONNECTED">CONNECTED</option>
          <option value="NO_ANSWER">NO_ANSWER</option>
          <option value="BUSY">BUSY</option>
          <option value="WRONG_NUMBER">WRONG_NUMBER</option>
          <option value="USER_REQUESTED_LATER">USER_REQUESTED_LATER</option>
          <option value="INVALID_CONTACT">INVALID_CONTACT</option>
        </select>
      </label>
      <label>
        开始时间
        <input v-model="contactStart" data-testid="contact-started-at" aria-label="contact started at" />
      </label>
      <label>
        结束时间
        <input v-model="contactEnd" data-testid="contact-ended-at" aria-label="contact ended at" />
      </label>
      <label>
        备注
        <input v-model="contactNote" data-testid="contact-note" aria-label="contact note" />
      </label>
      <button
        type="submit"
        data-testid="contact-submit"
        :disabled="contactBusy || !props.networkContextId"
      >
        记录联系
      </button>
      <ul data-testid="network-contact-list" class="appointments">
        <li v-for="item in contactAttempts" :key="item.contactAttemptId">
          {{ item.contactAttemptId }} · {{ item.resultCode }} · {{ item.channel }}
        </li>
      </ul>
      <p v-if="contactError" class="error" data-testid="contact-error">{{ contactError }}</p>
      <p v-if="contactMessage" class="ok" data-testid="contact-message">{{ contactMessage }}</p>
    </form>

    <form
      class="assign"
      data-testid="network-evidence-on-behalf-form"
      data-page-id="NETWORK.EVIDENCE.SUPPLEMENT"
      @submit.prevent="submitEvidenceBegin"
    >
      <h3>资料代补</h3>
      <p class="hint">
        调用 Network Portal begin/finalize on-behalf 与 correction resubmit；
        onBehalfOf 须为 ACTIVE TECHNICIAN。
      </p>
      <label>
        任务
        <select v-model="selectedTaskId" data-testid="evidence-task-select" aria-label="evidence task">
          <option disabled value="">选择任务</option>
          <option v-for="item in items" :key="item.taskId" :value="item.taskId">
            {{ item.taskId }}
          </option>
        </select>
      </label>
      <label>
        槽位 ID
        <input v-model="evidenceSlotId" data-testid="evidence-slot-id" aria-label="evidence slot id" />
      </label>
      <label>
        onBehalfOf
        <input v-model="onBehalfOf" data-testid="evidence-on-behalf-of" aria-label="on behalf of" />
      </label>
      <label>
        onBehalfReason
        <input
          v-model="onBehalfReason"
          data-testid="evidence-on-behalf-reason"
          aria-label="on behalf reason"
        />
      </label>
      <label>
        SHA-256
        <input v-model="evidenceSha256" data-testid="evidence-sha256" aria-label="evidence sha256" />
      </label>
      <label>
        文件名
        <input v-model="evidenceFileName" data-testid="evidence-file-name" aria-label="file name" />
      </label>
      <label>
        预期大小
        <input
          v-model.number="evidenceExpectedSize"
          type="number"
          data-testid="evidence-expected-size"
          aria-label="expected size"
        />
      </label>
      <label>
        上传会话 ID
        <input
          v-model="uploadSessionId"
          data-testid="evidence-upload-session-id"
          aria-label="upload session id"
        />
      </label>
      <button
        type="submit"
        data-testid="evidence-begin-submit"
        :disabled="evidenceBusy || !props.networkContextId"
      >
        Begin 代补上传
      </button>
      <button
        type="button"
        data-testid="evidence-finalize-submit"
        :disabled="evidenceBusy || !props.networkContextId"
        @click="submitEvidenceFinalize"
      >
        Finalize 代补上传
      </button>
      <p v-if="evidenceError" class="error" data-testid="evidence-on-behalf-error">{{ evidenceError }}</p>
      <p v-if="evidenceMessage" class="ok" data-testid="evidence-on-behalf-message">{{ evidenceMessage }}</p>
      <label>
        整改案例 ID
        <input
          v-model="correctionCaseId"
          data-testid="correction-case-id"
          aria-label="correction case id"
        />
      </label>
      <label>
        Snapshot ID
        <input
          v-model="evidenceSetSnapshotId"
          data-testid="evidence-set-snapshot-id"
          aria-label="evidence set snapshot id"
        />
      </label>
      <button
        type="button"
        data-testid="correction-resubmit-submit"
        :disabled="resubmitBusy || !props.networkContextId"
        @click="submitCorrectionResubmit"
      >
        整改 Resubmit
      </button>
      <p v-if="resubmitError" class="error" data-testid="correction-resubmit-error">{{ resubmitError }}</p>
      <p v-if="resubmitMessage" class="ok" data-testid="correction-resubmit-message">{{ resubmitMessage }}</p>
    </form>
  </section>
</template>

<style scoped>
.assign {
  margin-top: 1.5rem;
  display: grid;
  gap: 0.75rem;
  max-width: 32rem;
}
.assign label {
  display: grid;
  gap: 0.25rem;
}
.hint,
.filter {
  color: #555;
  font-size: 0.9rem;
}
.error {
  color: #a11;
}
.ok {
  color: #165;
}
.appointments {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.5rem;
}
.appointments li {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
</style>
