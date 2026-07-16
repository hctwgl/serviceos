<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
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

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    ...item,
    workspace: item.sourceWorkOrderId,
  })),
)

onMounted(() => load())
</script>

<template>
  <div>
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
  </div>
</template>

<style scoped>
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
