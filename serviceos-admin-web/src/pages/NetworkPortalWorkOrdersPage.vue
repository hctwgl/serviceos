<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listNetworkPortalTechnicians,
  listNetworkPortalWorkOrders,
  type NetworkPortalTechnicianItem,
  type NetworkPortalWorkOrderItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalWorkOrderItem[]>([])
const techniciansByProfileId = ref<Map<string, NetworkPortalTechnicianItem>>(new Map())
const error = ref<string | null>(null)

function technicianLabel(technicianId: string | null | undefined) {
  if (!technicianId) {
    return '—'
  }
  const tech = techniciansByProfileId.value.get(technicianId)
  return tech ? tech.displayName : technicianId
}

async function load() {
  if (!props.networkContextId) {
    items.value = []
    techniciansByProfileId.value = new Map()
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalWorkOrders(props.networkContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '工单列表加载失败'
  }
  try {
    const techPage = await listNetworkPortalTechnicians(props.networkContextId)
    const map = new Map<string, NetworkPortalTechnicianItem>()
    for (const item of techPage.items) {
      map.set(item.technicianProfileId, item)
    }
    techniciansByProfileId.value = map
  } catch {
    techniciansByProfileId.value = new Map()
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
    <p class="hint">M217：师傅 displayName fan-in；缺 technician.readOwnNetwork 时保留原始 ID。</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-work-orders-table">
      <thead>
        <tr>
          <th>工单</th>
          <th>项目</th>
          <th>任务数</th>
          <th>业务类型</th>
          <th>师傅</th>
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
