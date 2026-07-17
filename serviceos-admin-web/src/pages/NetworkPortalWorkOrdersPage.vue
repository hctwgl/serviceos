<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listNetworkPortalTechnicians,
  listNetworkPortalWorkOrders,
  type NetworkPortalTechnicianItem,
  type NetworkPortalWorkOrderItem,
  type NetworkPortalDirectorySlaRiskSummary,
  type NetworkPortalWorkspaceAppointmentSummary,
  type NetworkPortalWorkspaceContactAttemptSummary,
  type NetworkPortalWorkspaceCorrectionCaseSummary,
  type NetworkPortalWorkspaceEvidenceItemSummary,
  type NetworkPortalWorkspaceEvidenceSlotSummary,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalWorkOrderItem[]>([])
const techniciansByProfileId = ref<Map<string, NetworkPortalTechnicianItem>>(new Map())
const appointments = ref<NetworkPortalWorkspaceAppointmentSummary[] | null>(null)
const contactAttempts = ref<NetworkPortalWorkspaceContactAttemptSummary[] | null>(null)
const corrections = ref<NetworkPortalWorkspaceCorrectionCaseSummary[] | null>(null)
const evidenceSlots = ref<NetworkPortalWorkspaceEvidenceSlotSummary[] | null>(null)
const evidenceItems = ref<NetworkPortalWorkspaceEvidenceItemSummary[] | null>(null)
const slaRiskSummaries = ref<NetworkPortalDirectorySlaRiskSummary[] | null>(null)
const error = ref<string | null>(null)
const usedServerTechnicians = ref(false)

function technicianLabel(technicianId: string | null | undefined) {
  if (!technicianId) {
    return '—'
  }
  const tech = techniciansByProfileId.value.get(technicianId)
  return tech ? tech.displayName : technicianId
}

function applyTechnicians(rows: NetworkPortalTechnicianItem[]) {
  const map = new Map<string, NetworkPortalTechnicianItem>()
  for (const item of rows) {
    map.set(item.technicianProfileId, item)
  }
  techniciansByProfileId.value = map
}

function appointmentWindowLabel(taskIds: string[]) {
  if (appointments.value === null) {
    return '—'
  }
  const matched = appointments.value.filter((apt) => taskIds.includes(apt.taskId))
  if (matched.length === 0) {
    return '暂无'
  }
  const first = matched[0]
  if (!first.windowStart && !first.windowEnd) {
    return first.status
  }
  return `${first.windowStart ?? '?'} → ${first.windowEnd ?? '?'}（${first.status}）`
}

function contactLabel(taskIds: string[]) {
  if (contactAttempts.value === null) {
    return '—'
  }
  const matched = contactAttempts.value.filter((row) => taskIds.includes(row.taskId))
  if (matched.length === 0) {
    return '暂无'
  }
  const first = matched[0]
  return `${first.channel} · ${first.resultCode} · ${first.startedAt}`
}

function correctionLabel(taskIds: string[]) {
  if (corrections.value === null) {
    return '—'
  }
  const matched = corrections.value.filter((row) => taskIds.includes(row.taskId))
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

function evidenceLabel(taskIds: string[]) {
  if (evidenceSlots.value === null) {
    return '—'
  }
  const slots = evidenceSlots.value.filter((row) => taskIds.includes(row.taskId))
  const items = (evidenceItems.value ?? []).filter((row) => taskIds.includes(row.taskId))
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

function slaRiskLabel(workOrderId: string) {
  if (slaRiskSummaries.value === null) {
    return '—'
  }
  const matched = slaRiskSummaries.value.find((row) => row.workOrderId === workOrderId)
  if (!matched) {
    return '暂无'
  }
  return `开放 ${matched.openCount} / 超时 ${matched.breachedCount}`
}

async function load() {
  if (!props.networkContextId) {
    items.value = []
    techniciansByProfileId.value = new Map()
    appointments.value = null
    contactAttempts.value = null
    corrections.value = null
    evidenceSlots.value = null
    evidenceItems.value = null
    slaRiskSummaries.value = null
    usedServerTechnicians.value = false
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalWorkOrders(props.networkContextId)
    items.value = page.items
    appointments.value = page.appointments !== undefined ? page.appointments : null
    contactAttempts.value = page.contactAttempts !== undefined ? page.contactAttempts : null
    corrections.value = page.corrections !== undefined ? page.corrections : null
    evidenceSlots.value = page.evidenceSlots !== undefined ? page.evidenceSlots : null
    evidenceItems.value = page.evidenceItems !== undefined ? page.evidenceItems : null
    slaRiskSummaries.value = page.slaRiskSummaries !== undefined ? page.slaRiskSummaries : null
    error.value = null
    if (page.technicians !== undefined) {
      applyTechnicians(page.technicians)
      usedServerTechnicians.value = true
    } else {
      usedServerTechnicians.value = false
      try {
        const techPage = await listNetworkPortalTechnicians(props.networkContextId)
        applyTechnicians(techPage.items)
      } catch {
        techniciansByProfileId.value = new Map()
      }
    }
  } catch (err) {
    items.value = []
    techniciansByProfileId.value = new Map()
    appointments.value = null
    contactAttempts.value = null
    corrections.value = null
    evidenceSlots.value = null
    evidenceItems.value = null
    slaRiskSummaries.value = null
    usedServerTechnicians.value = false
    error.value = err instanceof Error ? err.message : '工单列表加载失败'
  }
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-work-orders" data-page-id="NETWORK.WORKORDER.LIST">
    <h2>本网点工单</h2>
    <p class="hint">
      <template v-if="usedServerTechnicians">
        M230～M235：师傅、预约窗口、最近联系、整改、资料与 SLA 风险由列表页服务端旁载交付。
      </template>
      <template v-else>
        M217：师傅 displayName fan-in；缺 technician.readOwnNetwork 时保留原始 ID。
      </template>
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-work-orders-table">
      <thead>
        <tr>
          <th>工单</th>
          <th>项目</th>
          <th>任务数</th>
          <th>业务类型</th>
          <th>师傅</th>
          <th v-if="appointments !== null">预约窗口</th>
          <th v-if="contactAttempts !== null">最近联系</th>
          <th v-if="evidenceSlots !== null">资料</th>
          <th v-if="corrections !== null">整改</th>
          <th v-if="slaRiskSummaries !== null">SLA 风险</th>
          <th>生效自</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in items"
          :key="item.workOrderId"
          :data-testid="`work-order-row-${item.workOrderId}`"
        >
          <td>
            <RouterLink
              :to="`/network-portal/work-orders/${item.workOrderId}`"
              data-testid="work-order-workspace-deeplink"
            >
              {{ item.workOrderId }}
            </RouterLink>
          </td>
          <td data-testid="work-order-project-id">{{ item.projectId ?? '—' }}</td>
          <td>{{ item.taskIds.length }}</td>
          <td>{{ item.businessType ?? '—' }}</td>
          <td data-testid="work-order-technician-label">
            {{ technicianLabel(item.technicianId) }}
          </td>
          <td
            v-if="appointments !== null"
            data-testid="work-order-appointment-window"
          >
            {{ appointmentWindowLabel(item.taskIds) }}
          </td>
          <td
            v-if="contactAttempts !== null"
            data-testid="work-order-contact-attempt"
          >
            {{ contactLabel(item.taskIds) }}
          </td>
          <td
            v-if="evidenceSlots !== null"
            data-testid="work-order-evidence-summary"
          >
            {{ evidenceLabel(item.taskIds) }}
          </td>
          <td
            v-if="corrections !== null"
            data-testid="work-order-correction-summary"
          >
            {{ correctionLabel(item.taskIds) }}
          </td>
          <td
            v-if="slaRiskSummaries !== null"
            data-testid="work-order-sla-risk"
          >
            {{ slaRiskLabel(item.workOrderId) }}
          </td>
          <td data-testid="work-order-effective-from">{{ item.effectiveFrom ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任工单</p>
  </section>
</template>

<style scoped>
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
</style>
