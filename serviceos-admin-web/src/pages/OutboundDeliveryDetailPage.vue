<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getOutboundDelivery,
  retryUnknownOutboundDelivery,
  type OutboundDelivery,
} from '../api/outbound'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const deliveryId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<OutboundDelivery | null>(null)
const reason = ref('')
const approvalRef = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getOutboundDelivery(deliveryId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载外发交付失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function retry() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!detail.value) throw new Error('交付未加载')
    if (detail.value.status !== 'UNKNOWN') throw new Error('仅 UNKNOWN 可人工重发')
    if (!reason.value.trim() || !approvalRef.value.trim()) {
      throw new Error('需要 reason 与 approvalRef')
    }
    const result = await retryUnknownOutboundDelivery(deliveryId.value, {
      expectedAggregateVersion: detail.value.aggregateVersion,
      reason: reason.value.trim(),
      approvalRef: approvalRef.value.trim(),
    })
    message.value = `已登记重发 ${result.data.replayRequestId} / task ${result.data.executionTaskId}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '重发失败'
  } finally {
    busy.value = false
  }
}

const attemptRows = computed(() =>
  (detail.value?.attempts ?? []).map((item, index) => ({
    index,
    ...item,
  })),
)

watch(deliveryId, () => {
  if (deliveryId.value) void load()
})
onMounted(() => {
  if (deliveryId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>外发交付</h2>
        <p class="meta">{{ deliveryId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.status }}</dd></div>
          <div><dt>externalOrderCode</dt><dd>{{ detail.externalOrderCode }}</dd></div>
          <div><dt>aggregateVersion</dt><dd>{{ detail.aggregateVersion }}</dd></div>
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
            <dt>sourceWorkOrderId</dt>
            <dd>
              <RouterLink
                :to="{
                  name: 'ADMIN.WORKORDER.WORKSPACE',
                  params: { id: detail.sourceWorkOrderId },
                }"
              >
                {{ detail.sourceWorkOrderId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>sourceReviewCaseId</dt>
            <dd>
              <RouterLink
                :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.sourceReviewCaseId } }"
              >
                {{ detail.sourceReviewCaseId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>sourceTaskId</dt>
            <dd>
              <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.sourceTaskId } }">
                {{ detail.sourceTaskId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>sourceSnapshotId</dt>
            <dd>
              <RouterLink
                :to="{
                  name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
                  params: { id: detail.sourceSnapshotId },
                }"
              >
                {{ detail.sourceSnapshotId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>clientReviewCaseId</dt>
            <dd>
              <RouterLink
                v-if="detail.clientReviewCaseId"
                :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.clientReviewCaseId } }"
              >
                {{ detail.clientReviewCaseId }}
              </RouterLink>
              <template v-else>-</template>
            </dd>
          </div>
          <!-- reviewRouteId 无 Implemented 详情契约，保持明文 -->
          <div><dt>reviewRouteId</dt><dd>{{ detail.reviewRouteId ?? '-' }}</dd></div>
        </dl>
        <p class="links outbound-cross-links">
          <RouterLink :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }">
            打开项目 {{ detail.projectId }}
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.sourceWorkOrderId } }">
            工单工作区
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.sourceReviewCaseId } }">
            源审核案例
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.sourceTaskId } }">
            打开源任务 {{ detail.sourceTaskId }}
          </RouterLink>
          <RouterLink
            :to="{
              name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
              params: { id: detail.sourceSnapshotId },
            }"
          >
            打开源资料快照 {{ detail.sourceSnapshotId }}
          </RouterLink>
          <RouterLink
            v-if="detail.clientReviewCaseId"
            :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.clientReviewCaseId } }"
          >
            CLIENT 审核案例
          </RouterLink>
        </p>
      </article>

      <article v-if="detail.status === 'UNKNOWN'" class="card">
        <h3>人工重发</h3>
        <label>reason<textarea v-model="reason" rows="3" /></label>
        <label>approvalRef<input v-model="approvalRef" /></label>
        <button type="button" :disabled="busy" @click="retry">retry UNKNOWN</button>
      </article>

      <QueueTable
        title="Attempts / Replays（摘要）"
        :columns="['index']"
        :rows="attemptRows"
        :loading="false"
        :error="null"
        :next-cursor="null"
        @refresh="load"
        @next="() => undefined"
      />
      <pre class="dump">{{ JSON.stringify({ attempts: detail.attempts, replayRequests: detail.replayRequests }, null, 2) }}</pre>
    </template>
  </section>
</template>

<style scoped>
.detail { display: grid; gap: 1rem; }
.top { display: flex; justify-content: space-between; }
.meta { margin: .25rem 0 0; color: #627d98; font-family: ui-monospace, monospace; font-size: .85rem; }
.card { background: #fff; border-radius: 12px; padding: 1rem 1.15rem; box-shadow: 0 1px 3px rgb(16 42 67 / 8%); display: grid; gap: .55rem; }
dl { margin: 0; display: grid; gap: .45rem; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); }
dt { font-size: .78rem; color: #627d98; }
dd { margin: .1rem 0 0; word-break: break-all; }
label { display: grid; gap: .25rem; font-size: .85rem; color: #486581; }
input, textarea, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.dump { background: #f0f4f8; border-radius: 8px; padding: .75rem; overflow: auto; max-height: 320px; font-size: .8rem; }
</style>
