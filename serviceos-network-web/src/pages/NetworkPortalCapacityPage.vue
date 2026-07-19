<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { onMounted, ref, watch } from 'vue'

import {
  listNetworkPortalCapacity,
  type NetworkPortalCapacityItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalCapacityItem[]>([])
const asOf = ref<string | null>(null)
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    asOf.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalCapacity(props.networkContextId)
    items.value = page.items
    asOf.value = page.asOf
    error.value = null
  } catch (err) {
    items.value = []
    asOf.value = null
    error.value = err instanceof Error ? err.message : '产能列表加载失败'
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
  <section data-testid="network-portal-capacity" data-page-id="NETWORK.CAPACITY">
    <h2>本网点产能</h2>
    <p class="hint">按业务类型展示在途占用与上限（只读）；含乐观版本供后续申请切片使用。</p>
    <p v-if="asOf" class="as-of" data-testid="capacity-as-of">asOf：{{ asOf }}</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-capacity-table">
      <thead>
        <tr>
          <th>业务类型</th>
          <th>占用</th>
          <th>上限</th>
          <th>可用</th>
          <th>version</th>
          <th>更新时间</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in items"
          :key="item.capacityCounterId"
          :data-testid="`capacity-row-${item.businessType}`"
        >
          <td>{{ item.businessType ? statusLabel(item.businessType) : '—' }}</td>
          <td>{{ item.occupiedUnits }}</td>
          <td>{{ item.maxUnits }}</td>
          <td>{{ item.availableUnits }}</td>
          <td data-testid="capacity-version">{{ item.version }}</td>
          <td>{{ item.updatedAt }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0" data-testid="capacity-empty">暂无产能计数</p>
  </section>
</template>

<style scoped>
.hint,
.as-of {
  color: #5b6573;
  font-size: 0.9rem;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.5rem 0.4rem;
  text-align: left;
}
</style>
