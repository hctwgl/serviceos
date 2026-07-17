<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getVisit, type Visit } from '../api/appointments'

const route = useRoute()
const visitId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<Visit | null>(null)
const etag = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await getVisit(visitId.value)
    detail.value = result.data
    etag.value = result.etag
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载上门详情失败'
    detail.value = null
    etag.value = null
  } finally {
    loading.value = false
  }
}

watch(visitId, () => {
  if (visitId.value) void load()
})
onMounted(() => {
  if (visitId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>上门详情</h2>
        <p class="meta">{{ visitId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.status }}</dd></div>
          <div><dt>visitSequence</dt><dd>{{ detail.visitSequence }}</dd></div>
          <div><dt>aggregateVersion</dt><dd>{{ detail.aggregateVersion }}</dd></div>
          <div><dt>technicianId</dt><dd>{{ detail.technicianId }}</dd></div>
          <div><dt>networkId</dt><dd>{{ detail.networkId || '—' }}</dd></div>
          <div><dt>geofenceResult</dt><dd>{{ detail.geofenceResult || '—' }}</dd></div>
          <div><dt>policyDecision</dt><dd>{{ detail.policyDecision || '—' }}</dd></div>
          <div><dt>deviceId</dt><dd>{{ detail.deviceId || '—' }}</dd></div>
          <div><dt>resultCode</dt><dd>{{ detail.resultCode || '—' }}</dd></div>
          <div><dt>exceptionCode</dt><dd>{{ detail.exceptionCode || '—' }}</dd></div>
          <div><dt>checkInCapturedAt</dt><dd>{{ detail.checkInCapturedAt || '—' }}</dd></div>
          <div><dt>checkOutCapturedAt</dt><dd>{{ detail.checkOutCapturedAt || '—' }}</dd></div>
          <div><dt>ETag</dt><dd>{{ etag || '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink
            :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.workOrderId } }"
          >
            工单工作区
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
          <RouterLink
            :to="{ name: 'ADMIN.APPOINTMENT.DETAIL', params: { id: detail.appointmentId } }"
          >
            预约详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{
        JSON.stringify(
          {
            checkInLocation: detail.checkInLocation ?? null,
            note: detail.note ?? null,
            operationRefs: detail.operationRefs ?? [],
            evidenceRefs: detail.evidenceRefs ?? [],
            allowedActions: detail.allowedActions ?? [],
          },
          null,
          2,
        )
      }}</pre>
    </template>
  </section>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
dl {
  margin: 0;
  display: grid;
  gap: 0.45rem;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
  word-break: break-all;
}
.links {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.75rem;
  flex-wrap: wrap;
}
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  font-size: 0.85rem;
}
.error {
  color: #9b1c1c;
}
button {
  border: 1px solid #243b53;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
