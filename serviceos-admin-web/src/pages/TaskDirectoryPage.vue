<script setup lang="ts">
import PageContainer from '../patterns/PageContainer.vue'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import SavedViewBar from '../components/SavedViewBar.vue'
import QueueTable from './QueueTable.vue'
import { listAuthorizedTasks, type TaskDirectoryPage } from '../api/tasksDirectory'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusLabel, statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  id: (row) => uuidRoute(row.id, 'ADMIN.TASK.DETAIL'),
  workOrderId: (row) => uuidRoute(row.workOrderId, 'ADMIN.WORKORDER.WORKSPACE'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const page = ref<TaskDirectoryPage | null>(null)
const cursor = ref<string | undefined>()
/** 默认不限；显式 route.query 可覆盖。 */
const status = ref('')
const taskKind = ref('')
const assigneeMe = ref(false)
const projectId = ref('')

const statusChoices = statusOptions([
  'READY',
  'CLAIMED',
  'RUNNING',
  'PENDING',
  'RETRY_WAIT',
  'SUCCEEDED',
  'MANUAL_INTERVENTION',
  'COMPLETED',
  'CANCELLED',
])

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextTaskKind = firstRouteQuery(route, 'taskKind')
  if (nextTaskKind !== undefined) {
    taskKind.value = nextTaskKind
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  // assignee=me 仅在显式 query 时开启；缺省保持侧栏直达的未勾选态。
  const nextAssignee = firstRouteQuery(route, 'assignee')
  if (nextAssignee !== undefined) {
    assigneeMe.value = nextAssignee === 'me'
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listAuthorizedTasks({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      taskKind: taskKind.value || undefined,
      assignee: assigneeMe.value ? 'me' : undefined,
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
  status.value = ''
  taskKind.value = ''
  assigneeMe.value = false
  projectId.value = ''
  return search()
}

function currentFilters() {
  return {
    status: status.value || undefined,
    taskKind: taskKind.value || undefined,
    assignee: assigneeMe.value ? 'me' : undefined,
    projectId: projectId.value.trim() || undefined,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status ?? ''
  taskKind.value = filters.taskKind ?? ''
  projectId.value = filters.projectId ?? ''
  assigneeMe.value = filters.assignee === 'me'
  return search()
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

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <PageContainer title="任务目录" description="查询履约任务并进入任务详情处理。">
    <SavedViewBar
      page-id="ADMIN.TASK.QUEUE"
      :schema-version="1"
      :current-filters="currentFilters()"
      @apply="applySavedView"
    />
    <form class="filters" @submit.prevent="search">
      <label>
        任务状态
        <select v-model="status" aria-label="task status filter">
          <option value="">（不限）</option>
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        任务类型
        <select v-model="taskKind" aria-label="task taskKind filter">
          <option value="">（不限）</option>
          <option value="HUMAN">人工任务</option>
          <option value="AUTOMATED">自动任务</option>
        </select>
      </label>
      <label>
        所属项目
        <input
          v-model="projectId"
          aria-label="task projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <label class="check">
        <input v-model="assigneeMe" type="checkbox" aria-label="task assignee me filter" />
        仅看我的任务
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
    </form>

    <QueueTable
      title="任务列表"
      :columns="['id', 'taskType', 'taskKind', 'status', 'priority', 'claimedBy', 'workOrderId', 'projectId', 'nextRunAt']"
      :column-labels="{
        id: '任务编号',
        taskType: '任务类型',
        taskKind: '执行方式',
        status: '任务状态',
        priority: '优先级',
        claimedBy: '认领人',
        workOrderId: '关联工单',
        nextRunAt: '下次执行时间',
      }"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有符合条件的任务；可调整筛选条件或查看其他项目。"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.id"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.id } }"
      >
        {{ statusLabel(item.taskType) }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.some((i) => i.workOrderId)" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items.filter((i) => i.workOrderId)"
        :key="`wo-${item.id}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        {{ statusLabel(item.taskType) }}
      </RouterLink>
    </p>
    <p
      v-if="page?.items?.some((i) => i.projectId)"
      class="links task-directory-cross-links"
    >
      打开关联资源：
      <RouterLink
        v-for="item in page.items.filter((i) => i.projectId)"
        :key="`project-${item.id}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId! } }"
      >
        打开项目 {{ item.projectId }}
      </RouterLink>
    </p>
  </PageContainer>
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
.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
select,
input,
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
