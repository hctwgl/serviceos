<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  listTechnicianSchedule,
  type TechnicianPortalScheduleItem,
} from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const route = useRoute()
const queryTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<TechnicianPortalScheduleItem[]>([])
const networkId = ref<string | null>(null)
const asOf = ref<string | null>(null)
const error = ref<string | null>(null)

const visibleItems = computed(() => {
  if (!queryTaskId.value) {
    return items.value
  }
  return items.value.filter((item) => item.taskId === queryTaskId.value)
})

async function load() {
  if (!props.technicianContextId) {
    items.value = []
    networkId.value = null
    asOf.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  try {
    const page = await listTechnicianSchedule(props.technicianContextId)
    items.value = page.items
    networkId.value = page.networkId
    asOf.value = page.asOf
    error.value = null
  } catch (err) {
    items.value = []
    networkId.value = null
    asOf.value = null
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
    <p class="hint">M218：展示 windowEnd/timezone 等 Accepted 字段；支持 taskId query 水合。</p>
    <p
      v-if="queryTaskId"
      class="filter"
      data-testid="schedule-task-filter"
    >
      已按 query 过滤 taskId：{{ queryTaskId }}
    </p>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <template v-else>
      <dl v-if="asOf" data-testid="technician-schedule-meta" class="meta">
        <div><dt>networkId</dt><dd data-testid="technician-schedule-network-id">{{ networkId }}</dd></div>
        <div><dt>asOf</dt><dd data-testid="technician-schedule-as-of">{{ asOf }}</dd></div>
      </dl>
      <table data-testid="technician-schedule-table">
        <thead>
          <tr>
            <th>预约</th>
            <th>任务</th>
            <th>工单</th>
            <th>项目</th>
            <th>类型</th>
            <th>状态</th>
            <th>窗口开始</th>
            <th>窗口结束</th>
            <th>时区</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in visibleItems"
            :key="item.appointmentId"
            :data-testid="`technician-schedule-row-${item.appointmentId}`"
            :data-filtered="queryTaskId && item.taskId === queryTaskId ? 'true' : 'false'"
          >
            <td>{{ item.appointmentId }}</td>
            <td data-testid="technician-schedule-task-id">{{ item.taskId }}</td>
            <td data-testid="technician-schedule-work-order-id">{{ item.workOrderId }}</td>
            <td data-testid="technician-schedule-project-id">{{ item.projectId ?? '—' }}</td>
            <td>{{ item.type }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.windowStart ?? '—' }}</td>
            <td data-testid="technician-schedule-window-end">{{ item.windowEnd ?? '—' }}</td>
            <td data-testid="technician-schedule-timezone">{{ item.timezone ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="visibleItems.length === 0" data-testid="technician-schedule-empty">
        {{ queryTaskId ? '无匹配预约' : '暂无预约日程' }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.hint,
.filter,
.meta {
  color: #5b6573;
  font-size: 0.9rem;
}
.meta {
  display: grid;
  gap: 0.25rem;
  margin: 0.75rem 0;
}
.meta dd {
  margin: 0 0 0.25rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.45rem 0.35rem;
  text-align: left;
  font-size: 0.8rem;
}
tr[data-filtered='true'] {
  background: #f0f9ff;
}
</style>
