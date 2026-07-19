<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listOperationalExceptions,
  type OperationalExceptionPage,
  type OperationalExceptionQueueQuery,
} from '../api/queues'
import { acknowledgeOperationalException } from '../api/exceptions'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  exceptionId: (row) => uuidRoute(row.exceptionId, 'ADMIN.EXCEPTION.DETAIL'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<OperationalExceptionPage | null>(null)
const cursor = ref<string | undefined>()
const note = ref('')
const busyId = ref<string | null>(null)

/**
 * 运营默认 OPEN（与既有硬编码一致）。
 * OpenAPI/服务端省略 status 表示不限；UI 提供空选项显式对应。
 * 深链 query 水合：仅在路由显式给出时覆盖默认值，避免侧栏直达行为漂移。
 */
const status = ref('OPEN')
const severity = ref('')
const category = ref('')
const projectId = ref('')
const workOrderId = ref('')
const taskId = ref('')

const statusChoices = statusOptions(['OPEN', 'ACKNOWLEDGED', 'RESOLVED'])

/**
 * 从 URL query 水合筛选表单。
 * status 允许空串表示「不限」；其余字段空串表示未筛选。
 */
function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextSeverity = firstRouteQuery(route, 'severity')
  if (nextSeverity !== undefined) {
    severity.value = nextSeverity
  }
  const nextCategory = firstRouteQuery(route, 'category')
  if (nextCategory !== undefined) {
    category.value = nextCategory
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  const nextWorkOrderId = firstRouteQuery(route, 'workOrderId')
  if (nextWorkOrderId !== undefined) {
    workOrderId.value = nextWorkOrderId
  }
  const nextTaskId = firstRouteQuery(route, 'taskId')
  if (nextTaskId !== undefined) {
    taskId.value = nextTaskId
  }
}

function queryParams(next?: string): OperationalExceptionQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    severity: severity.value || undefined,
    category: category.value.trim() || undefined,
    projectId: projectId.value.trim() || undefined,
    workOrderId: workOrderId.value.trim() || undefined,
    taskId: taskId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listOperationalExceptions(queryParams(next))
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
  status.value = 'OPEN'
  severity.value = ''
  category.value = ''
  projectId.value = ''
  workOrderId.value = ''
  taskId.value = ''
  return search()
}

async function acknowledge(exceptionId: string, aggregateVersion: number) {
  busyId.value = exceptionId
  message.value = null
  error.value = null
  errorCode.value = null
  try {
    const result = await acknowledgeOperationalException(
      exceptionId,
      aggregateVersion,
      note.value || null,
    )
    message.value = `已确认异常 ${result.data.exceptionId}`
    await load()
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    busyId.value = null
  }
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    exceptionId: item.exceptionId,
    projectId: item.projectId,
    severity: item.severity,
    category: item.category,
    status: item.status,
    errorCode: item.errorCode,
    openedAt: item.openedAt,
    aggregateVersion: item.aggregateVersion,
  })),
)

const acknowledgeable = computed(() =>
  (page.value?.items ?? []).filter(
    (item) => item.status === 'OPEN' && item.allowedActions?.includes('ACKNOWLEDGE'),
  ),
)

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1>异常中心</h1>
      <p class="desc">
        处理运营异常与自动化失败。默认展示待处理异常，可按严重程度、类别、项目或关联工单筛选。
      </p>
    </header>
    <form class="filters" @submit.prevent="search">
      <label>
        异常状态
        <select v-model="status" aria-label="exception status filter">
          <option value="">（不限）</option>
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        严重程度
        <select v-model="severity" aria-label="exception severity filter">
          <option value="">（不限）</option>
          <option value="P0">P0（紧急）</option>
          <option value="P1">P1（高）</option>
          <option value="P2">P2（中）</option>
          <option value="P3">P3（低）</option>
        </select>
      </label>
      <label>
        异常类别
        <input
          v-model="category"
          aria-label="exception category filter"
          placeholder="如 AUTOMATION_FINAL_FAILURE"
        />
      </label>
      <label>
        所属项目
        <input
          v-model="projectId"
          aria-label="exception projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <label>
        关联工单
        <input
          v-model="workOrderId"
          aria-label="exception workOrderId filter"
          placeholder="工单编号"
        />
      </label>
      <label>
        关联任务
        <input
          v-model="taskId"
          aria-label="exception taskId filter"
          placeholder="任务编号"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
    </form>

    <label class="note">
      确认备注（可选）
      <input v-model="note" maxlength="500" placeholder="人工接管说明" />
    </label>

    <QueueTable
      title="运营异常队列"
      :columns="[
        'exceptionId',
        'projectId',
        'severity',
        'category',
        'status',
        'errorCode',
        'openedAt',
        'aggregateVersion',
      ]"
      :column-labels="{
        exceptionId: '异常单号',
        projectId: '所属项目',
        severity: '严重程度',
        category: '异常类别',
        status: '异常状态',
        errorCode: '错误码',
        openedAt: '打开时间',
        aggregateVersion: '版本',
      }"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有运营异常，系统运行正常；或可调整筛选条件查看历史记录。"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="message" class="ok">{{ message }}</p>

    <div v-if="acknowledgeable.length" class="acks">
      <button
        v-for="item in acknowledgeable"
        :key="item.exceptionId"
        type="button"
        :disabled="busyId === item.exceptionId"
        @click="acknowledge(item.exceptionId, item.aggregateVersion)"
      >
        确认 {{ item.errorCode || item.exceptionId }}
      </button>
    </div>

    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.exceptionId"
        :to="{ name: 'ADMIN.EXCEPTION.DETAIL', params: { id: item.exceptionId } }"
      >
        打开异常
      </RouterLink>
    </p>
    <p v-if="page?.items?.some((i) => i.workOrderId)" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items.filter((i) => i.workOrderId)"
        :key="`wo-${item.exceptionId}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        打开工单
      </RouterLink>
    </p>
    <p
      v-if="
        page?.items?.some(
          (i) => i.projectId || i.taskId || i.handlingTaskId,
        )
      "
      class="links exception-queue-cross-links"
    >
      打开关联资源：
      <RouterLink
        v-for="item in page.items.filter((i) => i.projectId)"
        :key="`project-${item.exceptionId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId! } }"
      >
        打开项目
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.taskId)"
        :key="`task-${item.exceptionId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId! } }"
      >
        打开任务
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.handlingTaskId)"
        :key="`handling-${item.exceptionId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.handlingTaskId! } }"
      >
        打开人工接管任务
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
.note {
  display: grid;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  max-width: 480px;
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
.acks {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
}
.ok {
  color: #054e31;
}
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
