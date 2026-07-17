<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import SavedViewBar from '../components/SavedViewBar.vue'
import QueueTable from './QueueTable.vue'
import { listAuthorizedWorkOrders, type WorkOrderPage } from '../api/workOrders'
import { firstRouteQuery, uuidRoute } from '../routeQuery'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  id: (row) => uuidRoute(row.id, 'ADMIN.WORKORDER.WORKSPACE'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<WorkOrderPage | null>(null)
const cursor = ref<string | undefined>()
/** 默认不限；显式 route.query 可覆盖。 */
const status = ref('')
const clientCode = ref('')
const projectId = ref('')

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
    projectId.value = nextProjectId
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAuthorizedWorkOrders({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      clientCode: clientCode.value.trim() || undefined,
      projectId: projectId.value.trim() || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载工单目录失败'
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
    projectId: projectId.value.trim() || undefined,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status ?? ''
  clientCode.value = filters.clientCode ?? ''
  projectId.value = filters.projectId ?? ''
  return search()
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    id: item.id,
    externalOrderCode: item.externalOrderCode,
    status: item.status,
    clientCode: item.clientCode,
    projectId: item.projectId,
    receivedAt: item.receivedAt,
  })),
)

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <SavedViewBar
      page-id="ADMIN.WORKORDER.LIST"
      :schema-version="1"
      :current-filters="currentFilters()"
      @apply="applySavedView"
    />
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="workOrder status filter">
          <option value="">（不限）</option>
          <option value="RECEIVED">RECEIVED</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="FULFILLED">FULFILLED</option>
        </select>
      </label>
      <label>
        clientCode
        <input
          v-model="clientCode"
          aria-label="workOrder clientCode filter"
          placeholder="可选"
        />
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="workOrder projectId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="授权工单目录"
      :columns="['id', 'externalOrderCode', 'status', 'clientCode', 'projectId', 'receivedAt']"
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
      打开工作区：
      <RouterLink
        v-for="item in page.items"
        :key="item.id"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.id } }"
      >
        {{ item.externalOrderCode }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links work-order-directory-cross-links">
      打开关联资源：
      <RouterLink
        v-for="item in page.items"
        :key="`project-${item.id}`"
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
