<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listOutboundDeliveries,
  type OutboundDeliveryQueuePage,
  type OutboundDeliveryQueueQuery,
} from '../api/queues'
import { firstRouteQuery } from '../routeQuery'

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<OutboundDeliveryQueuePage | null>(null)
const cursor = ref<string | undefined>()

/** 与 OpenAPI 默认一致：省略或空时服务端仍按 UNKNOWN。显式 query 可覆盖。 */
const status = ref('UNKNOWN')
const businessMessageType = ref('')
const projectId = ref('')
const sourceWorkOrderId = ref('')
const sourceReviewCaseId = ref('')

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextBusinessMessageType = firstRouteQuery(route, 'businessMessageType')
  if (nextBusinessMessageType !== undefined) {
    businessMessageType.value = nextBusinessMessageType
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  const nextSourceWorkOrderId = firstRouteQuery(route, 'sourceWorkOrderId')
  if (nextSourceWorkOrderId !== undefined) {
    sourceWorkOrderId.value = nextSourceWorkOrderId
  }
  const nextSourceReviewCaseId = firstRouteQuery(route, 'sourceReviewCaseId')
  if (nextSourceReviewCaseId !== undefined) {
    sourceReviewCaseId.value = nextSourceReviewCaseId
  }
}

function queryParams(next?: string): OutboundDeliveryQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    businessMessageType: businessMessageType.value || undefined,
    projectId: projectId.value.trim() || undefined,
    sourceWorkOrderId: sourceWorkOrderId.value.trim() || undefined,
    sourceReviewCaseId: sourceReviewCaseId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOutboundDeliveries(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载外发队列失败'
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
    ...item,
    workspace: item.sourceWorkOrderId,
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
        <select v-model="status" aria-label="outbound status filter">
          <option value="UNKNOWN">UNKNOWN</option>
          <option value="PENDING">PENDING</option>
          <option value="SENDING">SENDING</option>
          <option value="DELIVERED">DELIVERED</option>
          <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="FAILED_FINAL">FAILED_FINAL</option>
        </select>
      </label>
      <label>
        businessMessageType
        <select v-model="businessMessageType" aria-label="outbound businessMessageType filter">
          <option value="">（不限）</option>
          <option value="SUBMIT_CLIENT_REVIEW">SUBMIT_CLIENT_REVIEW</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="outbound projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        sourceWorkOrderId
        <input
          v-model="sourceWorkOrderId"
          aria-label="outbound sourceWorkOrderId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        sourceReviewCaseId
        <input
          v-model="sourceReviewCaseId"
          aria-label="outbound sourceReviewCaseId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="外发交付队列"
      :columns="['deliveryId', 'projectId', 'status', 'externalOrderCode', 'attemptCount', 'createdAt', 'workspace']"
      :rows="rows"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />
    <p v-if="page?.items?.length" class="links">
      打开交付：
      <RouterLink
        v-for="item in page.items"
        :key="item.deliveryId"
        :to="{ name: 'ADMIN.INTEGRATION.DETAIL', params: { id: item.deliveryId } }"
      >
        {{ item.externalOrderCode || item.deliveryId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items"
        :key="`wo-${item.deliveryId}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.sourceWorkOrderId } }"
      >
        {{ item.sourceWorkOrderId }}
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
