<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listTechnicianTaskFeed, type TechnicianPortalFeedItem } from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const items = ref<TechnicianPortalFeedItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.technicianContextId) {
    items.value = []
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  try {
    const page = await listTechnicianTaskFeed(props.technicianContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '任务 Feed 加载失败'
  }
}

onMounted(() => {
  void load()
})
watch(() => props.technicianContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="technician-portal-task-feed">
    <h2>任务 Feed</h2>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <table v-else data-testid="technician-feed-table">
      <thead>
        <tr>
          <th>类型</th>
          <th>任务</th>
          <th>工单</th>
          <th>状态</th>
          <th>失效原因</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.cursor">
          <td>{{ item.itemType }}</td>
          <td>{{ item.taskId }}</td>
          <td>{{ item.workOrderId ?? '—' }}</td>
          <td>{{ item.taskStatus ?? '—' }}</td>
          <td>{{ item.invalidationReason ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任任务</p>
  </section>
</template>
