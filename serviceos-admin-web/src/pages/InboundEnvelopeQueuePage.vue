<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listInboundEnvelopes,
  type InboundEnvelopeQueuePage,
  type InboundEnvelopeQueueQuery,
} from '../api/queues'
import { firstRouteQuery } from '../routeQuery'

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<InboundEnvelopeQueuePage | null>(null)
const cursor = ref<string | undefined>()

/** 与 OpenAPI 默认一致：省略或空时服务端仍按 RECEIVED。显式 query 可覆盖。 */
const processingStatus = ref('RECEIVED')
const messageType = ref('')
const projectId = ref('')
const resultType = ref('')
const resultId = ref('')
const canonicalMessageId = ref('')

function hydrateFiltersFromRoute() {
  const nextProcessingStatus = firstRouteQuery(route, 'processingStatus')
  if (nextProcessingStatus !== undefined) {
    processingStatus.value = nextProcessingStatus
  }
  const nextMessageType = firstRouteQuery(route, 'messageType')
  if (nextMessageType !== undefined) {
    messageType.value = nextMessageType
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  const nextResultType = firstRouteQuery(route, 'resultType')
  if (nextResultType !== undefined) {
    resultType.value = nextResultType
  }
  const nextResultId = firstRouteQuery(route, 'resultId')
  if (nextResultId !== undefined) {
    resultId.value = nextResultId
  }
  const nextCanonicalMessageId = firstRouteQuery(route, 'canonicalMessageId')
  if (nextCanonicalMessageId !== undefined) {
    canonicalMessageId.value = nextCanonicalMessageId
  }
}

function queryParams(next?: string): InboundEnvelopeQueueQuery {
  return {
    cursor: next,
    limit: '20',
    processingStatus: processingStatus.value || undefined,
    messageType: messageType.value || undefined,
    projectId: projectId.value.trim() || undefined,
    resultType: resultType.value.trim() || undefined,
    resultId: resultId.value.trim() || undefined,
    canonicalMessageId: canonicalMessageId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listInboundEnvelopes(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载入站队列失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

const rows = computed(() => page.value?.items ?? [])

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="search">
      <label>
        processingStatus
        <select v-model="processingStatus" aria-label="inbound processingStatus filter">
          <option value="RECEIVED">RECEIVED</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
      </label>
      <label>
        messageType
        <select v-model="messageType" aria-label="inbound messageType filter">
          <option value="">（不限）</option>
          <option value="CREATE_WORK_ORDER">CREATE_WORK_ORDER</option>
          <option value="RECORD_CLIENT_REVIEW_RESULT">RECORD_CLIENT_REVIEW_RESULT</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="inbound projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        resultType
        <input
          v-model="resultType"
          aria-label="inbound resultType filter"
          placeholder="WORK_ORDER"
        />
      </label>
      <label>
        resultId
        <input
          v-model="resultId"
          aria-label="inbound resultId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        canonicalMessageId
        <input
          v-model="canonicalMessageId"
          aria-label="inbound canonicalMessageId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="入站 Envelope 队列"
      :columns="[
        'inboundEnvelopeId',
        'projectId',
        'messageType',
        'processingStatus',
        'externalMessageId',
        'resultType',
        'resultId',
        'receivedAt',
      ]"
      :rows="rows"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />
    <p v-if="page?.items?.length" class="links">
      打开入站：
      <RouterLink
        v-for="item in page.items"
        :key="item.inboundEnvelopeId"
        :to="{
          name: 'ADMIN.INTEGRATION.INBOUND.DETAIL',
          params: { id: item.inboundEnvelopeId },
        }"
      >
        {{ item.messageType }} / {{ item.externalMessageId || item.inboundEnvelopeId }}
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
