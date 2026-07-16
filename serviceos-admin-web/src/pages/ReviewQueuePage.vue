<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QueueTable from './QueueTable.vue'
import { listReviewCases, type ReviewCaseQueuePage } from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<ReviewCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listReviewCases({ cursor: next, limit: '20' })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载审核队列失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<template>
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
</template>
