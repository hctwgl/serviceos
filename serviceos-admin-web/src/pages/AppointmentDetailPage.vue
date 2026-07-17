<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getAppointment, type Appointment } from '../api/appointments'

const route = useRoute()
const appointmentId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<Appointment | null>(null)
const etag = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await getAppointment(appointmentId.value)
    detail.value = result.data
    etag.value = result.etag
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载预约详情失败'
    detail.value = null
    etag.value = null
  } finally {
    loading.value = false
  }
}

watch(appointmentId, () => {
  if (appointmentId.value) void load()
})
onMounted(() => {
  if (appointmentId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>预约详情</h2>
        <p class="meta">{{ appointmentId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.status }}</dd></div>
          <div><dt>type</dt><dd>{{ detail.type }}</dd></div>
          <div>
            <dt>projectId</dt>
            <dd>
              <RouterLink
                :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
              >
                {{ detail.projectId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>workOrderId</dt>
            <dd>
              <RouterLink
                :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.workOrderId } }"
              >
                {{ detail.workOrderId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>taskId</dt>
            <dd>
              <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
                {{ detail.taskId }}
              </RouterLink>
            </dd>
          </div>
          <div><dt>currentRevisionNo</dt><dd>{{ detail.currentRevisionNo }}</dd></div>
          <div><dt>aggregateVersion</dt><dd>{{ detail.aggregateVersion }}</dd></div>
          <!-- assignedNetworkId / technicianId 无 Implemented 目录详情页 -->
          <div><dt>assignedNetworkId</dt><dd>{{ detail.assignedNetworkId || '—' }}</dd></div>
          <div><dt>technicianId</dt><dd>{{ detail.technicianId || '—' }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
          <div><dt>createdBy</dt><dd>{{ detail.createdBy }}</dd></div>
          <div><dt>ETag</dt><dd>{{ etag || '—' }}</dd></div>
        </dl>
        <p class="links appointment-cross-links">
          <RouterLink
            :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
          >
            打开项目 {{ detail.projectId }}
          </RouterLink>
          <RouterLink
            :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.workOrderId } }"
          >
            工单工作区
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{ JSON.stringify({ revisions: detail.revisions ?? [], allowedActions: detail.allowedActions }, null, 2) }}</pre>
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
}
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  max-height: 320px;
  font-size: 0.8rem;
}
.error {
  color: #9b1c1c;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
