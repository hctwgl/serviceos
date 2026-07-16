<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listSlaInstances, type SlaInstancePage } from '../api/sla'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<SlaInstancePage | null>(null)
const cursor = ref<string | undefined>()
const status = ref('BREACHED')

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listSlaInstances({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 SLA 队列失败'
  } finally {
    loading.value = false
  }
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    slaInstanceId: item.slaInstanceId,
    status: item.status,
    slaRef: item.slaRef,
    deadlineAt: item.deadlineAt,
    remainingSeconds: item.remainingSeconds,
    overdueSeconds: item.overdueSeconds,
    workOrderId: item.workOrderId,
    taskId: item.taskId,
  })),
)

onMounted(() => load())
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="load()">
      <label>
        status
        <select v-model="status">
          <option value="">全部</option>
          <option value="RUNNING">RUNNING</option>
          <option value="BREACHED">BREACHED</option>
          <option value="MET">MET</option>
          <option value="MET_LATE">MET_LATE</option>
        </select>
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="SLA 工作台"
      :columns="['slaInstanceId', 'status', 'slaRef', 'deadlineAt', 'remainingSeconds', 'overdueSeconds', 'workOrderId']"
      :rows="rows"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="page?.items?.length" class="links">
      打开 SLA：
      <RouterLink
        v-for="item in page.items"
        :key="item.slaInstanceId"
        :to="{ name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } }"
      >
        {{ item.slaRef }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items"
        :key="`wo-${item.slaInstanceId}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        {{ item.workOrderId }}
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.filters {
  display: flex;
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
