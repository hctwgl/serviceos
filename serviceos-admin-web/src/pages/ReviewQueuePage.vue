<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listReviewCases,
  type ReviewCaseQueuePage,
  type ReviewCaseQueueQuery,
} from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<ReviewCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

/** 与 OpenAPI 省略默认一致：status=OPEN。 */
const status = ref('OPEN')
const origin = ref('')
const projectId = ref('')
const taskId = ref('')

function queryParams(next?: string): ReviewCaseQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    origin: origin.value || undefined,
    projectId: projectId.value.trim() || undefined,
    taskId: taskId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listReviewCases(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载审核队列失败'
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
        <select v-model="status" aria-label="review status filter">
          <option value="OPEN">OPEN</option>
          <option value="APPROVED">APPROVED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="FORCE_APPROVED">FORCE_APPROVED</option>
          <option value="REOPENED">REOPENED</option>
        </select>
      </label>
      <label>
        origin
        <select v-model="origin" aria-label="review origin filter">
          <option value="">（不限）</option>
          <option value="INTERNAL">INTERNAL</option>
          <option value="CLIENT">CLIENT</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="review projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        taskId
        <input
          v-model="taskId"
          aria-label="review taskId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="审核队列"
      :columns="['reviewCaseId', 'projectId', 'status', 'origin', 'createdAt', 'latestDecision']"
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
        :key="item.reviewCaseId"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.reviewCaseId } }"
      >
        打开审核案例 {{ item.reviewCaseId }}
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
