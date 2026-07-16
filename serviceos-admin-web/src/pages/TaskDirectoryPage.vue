<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listAuthorizedTasks, type TaskDirectoryPage } from '../api/tasksDirectory'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<TaskDirectoryPage | null>(null)
const cursor = ref<string | undefined>()
const status = ref('')
const taskKind = ref('')
const assigneeMe = ref(false)

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAuthorizedTasks({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      taskKind: taskKind.value || undefined,
      assignee: assigneeMe.value ? 'me' : undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载任务目录失败'
  } finally {
    loading.value = false
  }
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    id: item.id,
    taskType: item.taskType,
    taskKind: item.taskKind,
    status: item.status,
    priority: item.priority,
    claimedBy: item.claimedBy,
    workOrderId: item.workOrderId,
    nextRunAt: item.nextRunAt,
  })),
)

onMounted(() => load())
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="load()">
      <label>
        status
        <select v-model="status">
          <option value="">全部</option>
          <option value="READY">READY</option>
          <option value="CLAIMED">CLAIMED</option>
          <option value="RUNNING">RUNNING</option>
          <option value="PENDING">PENDING</option>
          <option value="RETRY_WAIT">RETRY_WAIT</option>
          <option value="MANUAL_INTERVENTION">MANUAL_INTERVENTION</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="CANCELLED">CANCELLED</option>
        </select>
      </label>
      <label>
        taskKind
        <select v-model="taskKind">
          <option value="">全部</option>
          <option value="HUMAN">HUMAN</option>
          <option value="AUTOMATED">AUTOMATED</option>
        </select>
      </label>
      <label class="check">
        <input v-model="assigneeMe" type="checkbox" />
        assignee=me
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="授权任务目录"
      :columns="['id', 'taskType', 'taskKind', 'status', 'priority', 'claimedBy', 'workOrderId', 'nextRunAt']"
      :rows="rows"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="page?.items?.some((i) => i.workOrderId)" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items.filter((i) => i.workOrderId)"
        :key="item.id"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        {{ item.taskType }}
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
  align-items: end;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
select,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
