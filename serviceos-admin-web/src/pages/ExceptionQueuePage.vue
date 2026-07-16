<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { listOperationalExceptions, type OperationalExceptionPage } from '../api/queues'
import { acknowledgeOperationalException } from '../api/exceptions'

const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<OperationalExceptionPage | null>(null)
const cursor = ref<string | undefined>()
const note = ref('')
const busyId = ref<string | null>(null)

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOperationalExceptions({ cursor: next, limit: '20', status: 'OPEN' })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载异常队列失败'
  } finally {
    loading.value = false
  }
}

async function acknowledge(exceptionId: string, aggregateVersion: number) {
  busyId.value = exceptionId
  message.value = null
  error.value = null
  try {
    const result = await acknowledgeOperationalException(exceptionId, aggregateVersion, note.value || null)
    message.value = `已确认 ${result.data.exceptionId}，version=${result.data.aggregateVersion}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '确认异常失败'
  } finally {
    busyId.value = null
  }
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    exceptionId: item.exceptionId,
    projectId: item.projectId,
    severity: item.severity,
    category: item.category,
    status: item.status,
    errorCode: item.errorCode,
    openedAt: item.openedAt,
    aggregateVersion: item.aggregateVersion,
  })),
)

const acknowledgeable = computed(() =>
  (page.value?.items ?? []).filter(
    (item) => item.status === 'OPEN' && item.allowedActions?.includes('ACKNOWLEDGE'),
  ),
)

onMounted(() => load())
</script>

<template>
  <section>
    <label class="note">
      确认备注（可选）
      <input v-model="note" maxlength="500" placeholder="人工接管说明" />
    </label>

    <QueueTable
      title="运营异常队列"
      :columns="['exceptionId', 'projectId', 'severity', 'category', 'status', 'errorCode', 'openedAt', 'aggregateVersion']"
      :rows="rows"
      :loading="loading"
      :error="error"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />

    <p v-if="message" class="ok">{{ message }}</p>

    <div v-if="acknowledgeable.length" class="acks">
      <button
        v-for="item in acknowledgeable"
        :key="item.exceptionId"
        type="button"
        :disabled="busyId === item.exceptionId"
        @click="acknowledge(item.exceptionId, item.aggregateVersion)"
      >
        确认 {{ item.errorCode || item.exceptionId }}
      </button>
    </div>

    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.exceptionId"
        :to="{ name: 'ADMIN.EXCEPTION.DETAIL', params: { id: item.exceptionId } }"
      >
        {{ item.errorCode || item.exceptionId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.some((i) => i.workOrderId)" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items.filter((i) => i.workOrderId)"
        :key="`wo-${item.exceptionId}`"
        :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.workOrderId } }"
      >
        {{ item.errorCode || item.exceptionId }}
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.note {
  display: grid;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  max-width: 480px;
  font-size: 0.85rem;
  color: #486581;
}
input {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
.acks {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
button {
  border: 0;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
}
.ok {
  color: #054e31;
}
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
