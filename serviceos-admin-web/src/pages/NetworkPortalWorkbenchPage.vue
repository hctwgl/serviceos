<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getNetworkPortalWorkbench, type NetworkPortalWorkbench } from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const data = ref<NetworkPortalWorkbench | null>(null)
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    data.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    data.value = await getNetworkPortalWorkbench(props.networkContextId)
    error.value = null
  } catch (err) {
    data.value = null
    error.value = err instanceof Error ? err.message : '工作台加载失败'
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
  <section data-testid="network-portal-workbench">
    <h2>本网点工作台</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <ul v-else-if="data" data-testid="network-workbench-counts">
      <li>ACTIVE 工单：{{ data.activeWorkOrderCount }}</li>
      <li>ACTIVE 任务：{{ data.activeTaskCount }}</li>
      <li>ACTIVE 师傅：{{ data.activeTechnicianCount }}</li>
    </ul>
  </section>
</template>
