<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listSlaInstances, type SlaInstancePage } from '../api/sla'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

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
const errorCode = ref<string | null>(null)
const page = ref<SlaInstancePage | null>(null)
const cursor = ref<string | undefined>()
/** 运营默认 BREACHED；省略 status 表示不限。显式 query 可覆盖。 */
const status = ref('BREACHED')
const projectId = ref('')

const statusChoices = statusOptions(['RUNNING', 'BREACHED', 'MET', 'MET_LATE'])

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
  errorCode.value = null
  try {
    page.value = await listSlaInstances({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      projectId: projectId.value.trim() || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

function resetFilters() {
  status.value = 'BREACHED'
  projectId.value = ''
  return search()
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
    <header class="page-head">
      <h1>时效中心</h1>
      <p class="desc">
        监控服务时效（SLA）达标与超时情况。默认展示已超时实例，可按状态与项目筛选。
      </p>
    </header>
    <form class="filters" @submit.prevent="search">
      <label>
        时效状态
        <select v-model="status" aria-label="sla status filter">
          <option value="">（不限）</option>
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        所属项目
        <input
          v-model="projectId"
          aria-label="sla projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
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
      :column-labels="{
        slaInstanceId: '时效单号',
        status: '时效状态',
        slaRef: '时效规则',
        deadlineAt: '截止时间',
        remainingSeconds: '剩余秒数',
        overdueSeconds: '超时秒数',
        projectId: '所属项目',
        workOrderId: '关联工单',
        taskId: '关联任务',
      }"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有符合条件的时效实例；所有 SLA 均在正常范围内，或可调整筛选条件。"
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
        打开工单
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links">
      打开关联任务：
      <RouterLink
        v-for="item in page.items"
        :key="`task-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
      >
        SLA / {{ item.slaRef || item.slaInstanceId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links sla-queue-cross-links">
      打开关联资源：
      <RouterLink
        v-for="item in page.items"
        :key="`project-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
      >
        打开项目
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.75rem;
}
.page-head h1 {
  margin: 0;
  font-size: 1.35rem;
}
.desc {
  margin: 0.35rem 0 0;
  color: #627d98;
  font-size: 0.92rem;
}
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
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
}
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
