<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QueueTable from './QueueTable.vue'
import { listOperationalExceptions, type OperationalExceptionPage } from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<OperationalExceptionPage | null>(null)
const cursor = ref<string | undefined>()

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOperationalExceptions({ cursor: next, limit: '20' })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载异常队列失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <QueueTable
    title="运营异常队列"
    :columns="['exceptionId', 'projectId', 'severity', 'category', 'status', 'errorCode', 'openedAt']"
    :rows="page?.items ?? []"
    :loading="loading"
    :error="error"
    :next-cursor="cursor ?? null"
    @refresh="load()"
    @next="load(cursor)"
  />
</template>
