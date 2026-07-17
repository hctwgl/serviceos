<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listNetworkPortalTechnicians,
  listNetworkPortalWorkOrders,
  type NetworkPortalTechnicianItem,
  type NetworkPortalWorkOrderItem,
  type NetworkPortalWorkspaceAppointmentSummary,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalWorkOrderItem[]>([])
const techniciansByProfileId = ref<Map<string, NetworkPortalTechnicianItem>>(new Map())
const appointments = ref<NetworkPortalWorkspaceAppointmentSummary[] | null>(null)
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

async function load() {
  if (!props.networkContextId) {
    items.value = []
    techniciansByProfileId.value = new Map()
    appointments.value = null
    usedServerTechnicians.value = false
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalWorkOrders(props.networkContextId)
    items.value = page.items
    appointments.value = page.appointments !== undefined ? page.appointments : null
    error.value = null
    if (page.technicians !== undefined) {
      // M230：服务端旁载替换 M217 client fan-in
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
        M230/M231：师傅与预约窗口由列表页服务端旁载交付。
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
