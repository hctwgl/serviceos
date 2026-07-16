<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QueueTable from './QueueTable.vue'
import { listOutboundDeliveries, type OutboundDeliveryQueuePage } from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<OutboundDeliveryQueuePage | null>(null)
const cursor = ref<string | undefined>()

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOutboundDeliveries({ cursor: next, limit: '20' })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载外发队列失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <QueueTable
    title="外发交付队列"
    :columns="['deliveryId', 'projectId', 'status', 'externalOrderCode', 'attemptCount', 'createdAt']"
    :rows="page?.items ?? []"
    :loading="loading"
    :error="error"
    :as-of="page?.asOf"
    :next-cursor="cursor ?? null"
    @refresh="load()"
    @next="load(cursor)"
  />
</template>
