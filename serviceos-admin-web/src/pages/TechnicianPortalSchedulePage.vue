<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listTechnicianSchedule, type TechnicianPortalScheduleItem } from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const items = ref<TechnicianPortalScheduleItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.technicianContextId) {
    items.value = []
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  try {
    const page = await listTechnicianSchedule(props.technicianContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '日程加载失败'
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
  <section data-testid="technician-portal-schedule">
    <h2>预约日程</h2>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <table v-else data-testid="technician-schedule-table">
      <thead>
        <tr>
          <th>预约</th>
          <th>任务</th>
          <th>类型</th>
          <th>状态</th>
          <th>窗口开始</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.appointmentId">
          <td>{{ item.appointmentId }}</td>
          <td>{{ item.taskId }}</td>
          <td>{{ item.type }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.windowStart ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无预约日程</p>
  </section>
</template>
