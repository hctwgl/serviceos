<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listOperationalExceptions,
  type OperationalExceptionPage,
  type OperationalExceptionQueueQuery,
} from '../api/queues'
import { acknowledgeOperationalException } from '../api/exceptions'

const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<OperationalExceptionPage | null>(null)
const cursor = ref<string | undefined>()
const note = ref('')
const busyId = ref<string | null>(null)

/**
 * 运营默认 OPEN（与既有硬编码一致）。
 * OpenAPI/服务端省略 status 表示不限；UI 提供空选项显式对应。
 */
const status = ref('OPEN')
const severity = ref('')
const category = ref('')
const projectId = ref('')
const workOrderId = ref('')
const taskId = ref('')

function queryParams(next?: string): OperationalExceptionQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    severity: severity.value || undefined,
    category: category.value.trim() || undefined,
    projectId: projectId.value.trim() || undefined,
    workOrderId: workOrderId.value.trim() || undefined,
    taskId: taskId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOperationalExceptions(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载异常队列失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

async function acknowledge(exceptionId: string, aggregateVersion: number) {
  busyId.value = exceptionId
  message.value = null
  error.value = null
  try {
    const result = await acknowledgeOperationalException(
      exceptionId,
      aggregateVersion,
      note.value || null,
    )
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
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="exception status filter">
          <option value="">（不限）</option>
          <option value="OPEN">OPEN</option>
          <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          <option value="RESOLVED">RESOLVED</option>
        </select>
      </label>
      <label>
        severity
        <select v-model="severity" aria-label="exception severity filter">
          <option value="">（不限）</option>
          <option value="P0">P0</option>
          <option value="P1">P1</option>
          <option value="P2">P2</option>
          <option value="P3">P3</option>
        </select>
      </label>
      <label>
        category
        <input
          v-model="category"
          aria-label="exception category filter"
          placeholder="e.g. AUTOMATION_FINAL_FAILURE"
        />
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="exception projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        workOrderId
        <input
          v-model="workOrderId"
          aria-label="exception workOrderId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        taskId
        <input
          v-model="taskId"
          aria-label="exception taskId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <label class="note">
      确认备注（可选）
      <input v-model="note" maxlength="500" placeholder="人工接管说明" />
    </label>

    <QueueTable
      title="运营异常队列"
      :columns="[
        'exceptionId',
        'projectId',
        'severity',
        'category',
        'status',
        'errorCode',
        'openedAt',
        'aggregateVersion',
      ]"
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
        打开异常 {{ item.exceptionId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.some((i) => i.workOrderId)" class="links">
      打开工作区：
      <RouterLink
        v-for="item in page.items.filter((i) => i.workOrderId)"
        :key="`wo-${item.exceptionId}`"
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
.note {
  display: grid;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  max-width: 480px;
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
.acks {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
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
