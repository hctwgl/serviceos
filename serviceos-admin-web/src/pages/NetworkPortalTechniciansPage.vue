<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  listNetworkPortalTechnicians,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTechnicianItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalTechnicians(props.networkContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '师傅列表加载失败'
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
  <section data-testid="network-portal-technicians">
    <h2>本网点师傅</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-technicians-table">
      <thead>
        <tr>
          <th>姓名</th>
          <th>档案状态</th>
          <th>关系状态</th>
          <th>档案 ID</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.membershipId">
          <td>{{ item.displayName }}</td>
          <td>{{ item.profileStatus }}</td>
          <td>{{ item.membershipStatus }}</td>
          <td>{{ item.technicianProfileId }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 师傅关系</p>
  </section>
</template>
