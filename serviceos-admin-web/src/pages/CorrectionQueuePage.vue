<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listCorrectionCases, type CorrectionCaseQueuePage } from '../api/queues'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<CorrectionCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listCorrectionCases({ cursor: next, limit: '20' })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载整改队列失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <section>
    <QueueTable
      title="整改跟踪"
      :columns="['correctionCaseId', 'projectId', 'status', 'createdAt', 'resubmissionCount']"
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
        {{ item.status }}
      </RouterLink>
    </p>
  </section>
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
