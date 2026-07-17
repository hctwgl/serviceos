<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  listNetworkPortalWorkOrders,
  type NetworkPortalWorkOrderItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalWorkOrderItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
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
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-work-orders">
    <h2>本网点工单</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-work-orders-table">
      <thead>
        <tr>
          <th>工单</th>
          <th>任务数</th>
          <th>业务类型</th>
          <th>师傅</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.workOrderId">
          <td>{{ item.workOrderId }}</td>
          <td>{{ item.taskIds.length }}</td>
          <td>{{ item.businessType ?? '—' }}</td>
          <td>{{ item.technicianId ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任工单</p>
  </section>
</template>
