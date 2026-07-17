<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listCorrectionCases,
  type CorrectionCaseQueuePage,
  type CorrectionCaseQueueQuery,
} from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<CorrectionCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

/**
 * 运营默认 IN_PROGRESS（与既有 Admin 客户端一致）；
 * OpenAPI 省略 status 时服务端默认 OPEN，故 UI 必须显式传 IN_PROGRESS。
 */
const status = ref('IN_PROGRESS')
const projectId = ref('')
const taskId = ref('')
const sourceReviewCaseId = ref('')

function queryParams(next?: string): CorrectionCaseQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    projectId: projectId.value.trim() || undefined,
    taskId: taskId.value.trim() || undefined,
    sourceReviewCaseId: sourceReviewCaseId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listCorrectionCases(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载整改队列失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

onMounted(() => load())
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="correction status filter">
          <option value="OPEN">OPEN</option>
          <option value="IN_PROGRESS">IN_PROGRESS</option>
          <option value="RESUBMITTED">RESUBMITTED</option>
          <option value="CLOSED">CLOSED</option>
          <option value="WAIVED">WAIVED</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="correction projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        taskId
        <input
          v-model="taskId"
          aria-label="correction taskId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        sourceReviewCaseId
        <input
          v-model="sourceReviewCaseId"
          aria-label="correction sourceReviewCaseId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="整改跟踪"
      :columns="[
        'correctionCaseId',
        'sourceReviewCaseId',
        'correctionTaskId',
        'status',
        'createdAt',
        'resubmissionCount',
      ]"
      :rows="page?.items ?? []"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />
    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.correctionCaseId"
        :to="{ name: 'ADMIN.CORRECTION.DETAIL', params: { id: item.correctionCaseId } }"
      >
        打开整改案例 {{ item.correctionCaseId }}
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
