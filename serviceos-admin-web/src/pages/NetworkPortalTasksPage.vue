<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listNetworkPortalTasks, type NetworkPortalTaskItem } from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTaskItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalTasks(props.networkContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '任务列表加载失败'
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
  <section data-testid="network-portal-tasks">
    <h2>本网点任务</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-tasks-table">
      <thead>
        <tr>
          <th>任务</th>
          <th>工单</th>
          <th>状态</th>
          <th>类型</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.taskId">
          <td>{{ item.taskId }}</td>
          <td>{{ item.workOrderId }}</td>
          <td>{{ item.status ?? '—' }}</td>
          <td>{{ item.taskType ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任任务</p>
  </section>
</template>
