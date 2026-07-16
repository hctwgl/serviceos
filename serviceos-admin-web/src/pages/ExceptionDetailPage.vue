<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  acknowledgeOperationalException,
  getOperationalException,
  type OperationalException,
} from '../api/exceptions'

const route = useRoute()
const exceptionId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<OperationalException | null>(null)
const note = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getOperationalException(exceptionId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载异常详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function acknowledge() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!detail.value) throw new Error('无详情')
    const result = await acknowledgeOperationalException(
      detail.value.exceptionId,
      detail.value.aggregateVersion,
      note.value || null,
    )
    message.value = `已确认 ${result.data.exceptionId} / v${result.data.aggregateVersion}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '确认异常失败'
  } finally {
    busy.value = false
  }
}

watch(exceptionId, () => {
  if (exceptionId.value) void load()
})
onMounted(() => {
  if (exceptionId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>运营异常详情</h2>
        <p class="meta">{{ exceptionId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="detail">
      <article class="card">
        <h3>{{ detail.errorCode }} / {{ detail.status }}</h3>
        <dl>
          <div><dt>severity</dt><dd>{{ detail.severity }}</dd></div>
          <div><dt>category</dt><dd>{{ detail.category }}</dd></div>
          <div><dt>projectId</dt><dd>{{ detail.projectId || '—' }}</dd></div>
          <div><dt>workOrderId</dt><dd>{{ detail.workOrderId || '—' }}</dd></div>
          <div><dt>taskId</dt><dd>{{ detail.taskId || '—' }}</dd></div>
          <div><dt>sourceType</dt><dd>{{ detail.sourceType }}</dd></div>
          <div><dt>sourceId</dt><dd>{{ detail.sourceId }}</dd></div>
          <div><dt>sourceAttemptId</dt><dd>{{ detail.sourceAttemptId }}</dd></div>
          <div><dt>sourceTaskType</dt><dd>{{ detail.sourceTaskType }}</dd></div>
          <div><dt>occurrenceCount</dt><dd>{{ detail.occurrenceCount }}</dd></div>
          <div><dt>aggregateVersion</dt><dd>{{ detail.aggregateVersion }}</dd></div>
          <div><dt>openedAt</dt><dd>{{ detail.openedAt }}</dd></div>
          <div><dt>lastDetectedAt</dt><dd>{{ detail.lastDetectedAt }}</dd></div>
          <div><dt>acknowledgedAt</dt><dd>{{ detail.acknowledgedAt || '—' }}</dd></div>
          <div><dt>acknowledgedBy</dt><dd>{{ detail.acknowledgedBy || '—' }}</dd></div>
          <div><dt>acknowledgementNote</dt><dd>{{ detail.acknowledgementNote || '—' }}</dd></div>
          <div><dt>resolvedAt</dt><dd>{{ detail.resolvedAt || '—' }}</dd></div>
          <div><dt>resolutionCode</dt><dd>{{ detail.resolutionCode || '—' }}</dd></div>
          <div><dt>allowedActions</dt><dd>{{ detail.allowedActions.join(', ') || '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink v-if="detail.workOrderId" :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.workOrderId } }">
            打开工单工作区
          </RouterLink>
          <RouterLink v-if="detail.taskId" :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            打开任务详情
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.EXCEPTION.QUEUE' }">返回异常队列</RouterLink>
        </p>
      </article>

      <article v-if="detail.allowedActions.includes('ACKNOWLEDGE')" class="card form">
        <h3>确认异常</h3>
        <label>note（可选）<input v-model="note" maxlength="500" /></label>
        <button type="button" :disabled="busy" @click="acknowledge">acknowledge</button>
        <p v-if="message" class="ok">{{ message }}</p>
      </article>
    </template>
  </section>
</template>

<style scoped>
.detail { display: grid; gap: 1rem; }
.top { display: flex; justify-content: space-between; }
.meta { margin: .25rem 0 0; color: #627d98; font-family: ui-monospace, monospace; font-size: .85rem; }
.card { background: #fff; border-radius: 12px; padding: 1rem 1.15rem; box-shadow: 0 1px 3px rgb(16 42 67 / 8%); }
.form { display: grid; gap: .55rem; }
label { display: grid; gap: .25rem; font-size: .85rem; color: #486581; }
input, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; }
dl { margin: 0; display: grid; gap: .5rem; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
dt { font-size: .78rem; color: #627d98; }
dd { margin: .1rem 0 0; word-break: break-all; }
.links { margin-top: .75rem; display: flex; flex-wrap: wrap; gap: .75rem; }
.error { color: #9b1c1c; }
.ok { color: #054e31; margin: 0; }
</style>
