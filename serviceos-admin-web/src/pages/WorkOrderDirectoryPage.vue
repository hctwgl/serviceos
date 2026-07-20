<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, type RouteLocationRaw } from 'vue-router'
import SavedViewBar from '../components/SavedViewBar.vue'
import QueueTable from './QueueTable.vue'
import { listAuthorizedWorkOrders, type WorkOrderPage } from '../api/workOrders'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

const statusChoices = statusOptions(['RECEIVED', 'ACTIVE', 'FULFILLED', 'CANCELLED'])

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  externalOrderCode: (row) => uuidRoute(row.id, 'ADMIN.WORKORDER.WORKSPACE'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const page = ref<WorkOrderPage | null>(null)
const cursor = ref<string | undefined>()
/** 默认不限；显式 route.query 可覆盖。 */
const status = ref('')
const clientCode = ref('')
const projectKeyword = ref('')
const keyword = ref('')

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextClientCode = firstRouteQuery(route, 'clientCode')
  if (nextClientCode !== undefined) {
    clientCode.value = nextClientCode
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectKeyword.value = nextProjectId
  }
  const nextKeyword = firstRouteQuery(route, 'q')
  if (nextKeyword !== undefined) {
    keyword.value = nextKeyword
  }
}

function looksLikeUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listAuthorizedWorkOrders({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      clientCode: clientCode.value.trim() || undefined,
      projectId: looksLikeUuid(projectKeyword.value)
        ? projectKeyword.value.trim()
        : undefined,
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

function currentFilters() {
  return {
    status: status.value || undefined,
    clientCode: clientCode.value.trim() || undefined,
    projectId: looksLikeUuid(projectKeyword.value)
      ? projectKeyword.value.trim()
      : undefined,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status ?? ''
  clientCode.value = filters.clientCode ?? ''
  projectKeyword.value = filters.projectId ?? ''
  return search()
}

function resetFilters() {
  status.value = ''
  clientCode.value = ''
  projectKeyword.value = ''
  keyword.value = ''
  return search()
}

const rows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const project = projectKeyword.value.trim().toLowerCase()
  return (page.value?.items ?? [])
    .filter((item) => {
      if (q) {
        const hay = `${item.externalOrderCode} ${item.clientCode} ${item.id}`.toLowerCase()
        if (!hay.includes(q)) {
          return false
        }
      }
      if (project && !looksLikeUuid(projectKeyword.value)) {
        if (!item.projectId.toLowerCase().includes(project) && !item.clientCode.toLowerCase().includes(project)) {
          return false
        }
      }
      return true
    })
    .map((item) => ({
      externalOrderCode: item.externalOrderCode,
      status: item.status,
      clientCode: item.clientCode,
      projectId: item.projectId,
      receivedAt: item.receivedAt,
      id: item.id,
    }))
})

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1>工单中心</h1>
      <p class="desc">按状态与关键字查找工单，第一列展示业务工单编号而非内部 ID。</p>
    </header>
    <SavedViewBar
      page-id="ADMIN.WORKORDER.LIST"
      :schema-version="1"
      :current-filters="currentFilters()"
      @apply="applySavedView"
    />
    <form class="filters" @submit.prevent="search">
      <label>
        工单状态
        <select v-model="status" aria-label="workOrder status filter">
          <option value="">（不限）</option>
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        车企
        <input
          v-model="clientCode"
          aria-label="workOrder clientCode filter"
          placeholder="如 GEELY / BYD"
        />
      </label>
      <label>
        所属项目
        <input
          v-model="projectKeyword"
          aria-label="workOrder projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <label>
        关键字
        <input
          v-model="keyword"
          aria-label="workOrder keyword filter"
          placeholder="工单编号 / 车企"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
    </form>

    <QueueTable
      title="工单列表"
      :columns="['externalOrderCode', 'status', 'clientCode', 'projectId', 'receivedAt']"
      :column-labels="{
        externalOrderCode: '工单编号',
        status: '当前状态',
        clientCode: '车企',
        projectId: '所属项目',
        receivedAt: '创建时间',
      }"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有工单。可到「演示数据管理」初始化演示工单，或调整筛选条件。"
      @refresh="load()"
      @next="load(cursor)"
    />

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
input,
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
