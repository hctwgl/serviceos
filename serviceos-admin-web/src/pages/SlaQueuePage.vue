<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listSlaInstances, type SlaInstancePage } from '../api/sla'
import { firstRouteQuery, uuidRoute } from '../routeQuery'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  slaInstanceId: (row) => uuidRoute(row.slaInstanceId, 'ADMIN.SLA.DETAIL'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
  workOrderId: (row) => uuidRoute(row.workOrderId, 'ADMIN.WORKORDER.WORKSPACE'),
  taskId: (row) => uuidRoute(row.taskId, 'ADMIN.TASK.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<SlaInstancePage | null>(null)
const cursor = ref<string | undefined>()
/** 运营默认 BREACHED；省略 status 表示不限。显式 query 可覆盖。 */
const status = ref('BREACHED')
const projectId = ref('')

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listSlaInstances({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      projectId: projectId.value.trim() || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 SLA 队列失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    slaInstanceId: item.slaInstanceId,
    status: item.status,
    slaRef: item.slaRef,
    deadlineAt: item.deadlineAt,
    remainingSeconds: item.remainingSeconds,
    overdueSeconds: item.overdueSeconds,
    projectId: item.projectId,
    workOrderId: item.workOrderId,
    taskId: item.taskId,
  })),
)

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="sla status filter">
          <option value="">（不限）</option>
          <option value="RUNNING">RUNNING</option>
          <option value="BREACHED">BREACHED</option>
          <option value="MET">MET</option>
          <option value="MET_LATE">MET_LATE</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="sla projectId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="SLA 工作台"
      :columns="[
        'slaInstanceId',
        'status',
        'slaRef',
        'deadlineAt',
        'remainingSeconds',
        'overdueSeconds',
        'projectId',
        'workOrderId',
        'taskId',
      ]"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="page?.items?.length" class="links">
      打开 SLA：
      <RouterLink
        v-for="item in page.items"
        :key="item.slaInstanceId"
        :to="{ name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } }"
      >
        {{ item.slaRef }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items"
        :key="`wo-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        {{ item.workOrderId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links">
      打开关联任务：
      <RouterLink
        v-for="item in page.items"
        :key="`task-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
      >
        <!-- 三段标签：避免与权威区「taskType / taskId」strict 冲突 -->
        SLA / {{ item.slaRef || item.slaInstanceId }} / {{ item.taskId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links sla-queue-cross-links">
      打开关联资源：
      <RouterLink
        v-for="item in page.items"
        :key="`project-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
      >
        打开项目 {{ item.projectId }}
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
select,
input,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
input {
  min-width: 12rem;
  font-family: ui-monospace, monospace;
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
