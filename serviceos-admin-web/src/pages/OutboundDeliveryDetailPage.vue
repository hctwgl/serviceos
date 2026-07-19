<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getOutboundDelivery,
  recordManualOutboundAck,
  retryUnknownOutboundDelivery,
  type ManualOutboundDisposition,
  type OutboundDelivery,
} from '../api/outbound'
import { hasCapability, loadActiveCapabilityCodes } from '../api/capabilitiesGate'
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
const externalRef = ref('')
const evidenceRefsText = ref('')
const capabilityCodes = ref<string[]>([])
const lastDisposition = ref<ManualOutboundDisposition | null>(null)

// retry 保持既有 status 门禁；capability 仅作软提示，后端仍失败关闭。
const canRetry = computed(
  () => detail.value?.status === 'UNKNOWN' && !lastDisposition.value,
)
const missingRetryCapability = computed(
  () =>
    canRetry.value &&
    capabilityCodes.value.length > 0 &&
    !hasCapability(capabilityCodes.value, 'integration.retryUnknownDelivery'),
)
const canDispose = computed(
  () =>
    detail.value?.status === 'UNKNOWN' &&
    !lastDisposition.value &&
    (capabilityCodes.value.length === 0 ||
      hasCapability(capabilityCodes.value, 'integration.recordManualOutboundAck')),
)

async function loadCaps() {
  capabilityCodes.value = await loadActiveCapabilityCodes()
}

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

async function dispose(result: 'MANUAL_CONFIRMED' | 'ABANDONED') {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!detail.value) throw new Error('交付未加载')
    if (detail.value.status !== 'UNKNOWN') throw new Error('仅 UNKNOWN 可人工处置')
    if (!reason.value.trim() || !approvalRef.value.trim()) {
      throw new Error('需要 reason 与 approvalRef')
    }
    const evidenceRefs = evidenceRefsText.value
      .split(/[\n,]/)
      .map((item) => item.trim())
      .filter(Boolean)
    if (result === 'MANUAL_CONFIRMED' && !externalRef.value.trim() && evidenceRefs.length === 0) {
      throw new Error('MANUAL_CONFIRMED 需要 externalRef 或 evidenceRefs')
    }
    const disposition = await recordManualOutboundAck(deliveryId.value, {
      expectedAggregateVersion: detail.value.aggregateVersion,
      result,
      reason: reason.value.trim(),
      approvalRef: approvalRef.value.trim(),
      externalRef: externalRef.value.trim() || null,
      evidenceRefs,
    })
    lastDisposition.value = disposition.data
    message.value = `已登记人工处置 ${disposition.data.result} / ${disposition.data.dispositionId}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '人工处置失败'
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
  lastDisposition.value = null
  if (deliveryId.value) void load()
})
onMounted(() => {
  void loadCaps()
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
          <div><dt>businessMessageType</dt><dd>{{ detail.businessMessageType }}</dd></div>
          <div><dt>businessKey</dt><dd>{{ detail.businessKey }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
          <div><dt>deliveredAt</dt><dd>{{ detail.deliveredAt ?? '—' }}</dd></div>
          <div><dt>acknowledgedAt</dt><dd>{{ detail.acknowledgedAt ?? '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.sourceReviewCaseId } }">
            源审核案例
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.sourceTaskId } }">
            打开源任务 {{ detail.sourceTaskId }}
          </RouterLink>
          <RouterLink
            v-if="detail.executionTaskId"
            :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.executionTaskId } }"
          >
            打开执行任务 {{ detail.executionTaskId }}
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

      <article v-if="lastDisposition" class="card" data-testid="manual-disposition-result">
        <h3>已登记人工处置</h3>
        <dl>
          <div><dt>result</dt><dd>{{ lastDisposition.result }}</dd></div>
          <div><dt>dispositionId</dt><dd>{{ lastDisposition.dispositionId }}</dd></div>
          <div><dt>requestedBy</dt><dd>{{ lastDisposition.requestedBy }}</dd></div>
          <div><dt>requestedAt</dt><dd>{{ lastDisposition.requestedAt }}</dd></div>
        </dl>
        <p class="meta">Delivery 状态保持 UNKNOWN；已禁止再次 retry。</p>
      </article>

      <article v-if="canRetry" class="card" data-testid="unknown-retry-card">
        <h3>人工重发</h3>
        <p v-if="missingRetryCapability" class="meta">
          当前上下文未见 integration.retryUnknownDelivery；提交仍可能被后端拒绝。
        </p>
        <label>reason<textarea v-model="reason" rows="3" /></label>
        <label>approvalRef<input v-model="approvalRef" /></label>
        <button type="button" :disabled="busy" data-testid="retry-unknown" @click="retry">
          retry UNKNOWN
        </button>
      </article>

      <article v-if="canDispose" class="card" data-testid="manual-disposition-card">
        <h3>人工确认 / 放弃</h3>
        <p class="meta">
          MANUAL_CONFIRMED / ABANDONED 不改写 Delivery 状态为 ACKNOWLEDGED；高风险一次性处置。
        </p>
        <label>reason<textarea v-model="reason" rows="3" data-testid="disposition-reason" /></label>
        <label>approvalRef<input v-model="approvalRef" data-testid="disposition-approval-ref" /></label>
        <label>
          externalRef（MANUAL_CONFIRMED 可用）
          <input v-model="externalRef" data-testid="disposition-external-ref" />
        </label>
        <label>
          evidenceRefs（逗号或换行分隔）
          <textarea v-model="evidenceRefsText" rows="2" data-testid="disposition-evidence-refs" />
        </label>
        <div class="actions">
          <button
            type="button"
            :disabled="busy"
            data-testid="disposition-confirm"
            @click="dispose('MANUAL_CONFIRMED')"
          >
            MANUAL_CONFIRMED
          </button>
          <button
            type="button"
            class="danger"
            :disabled="busy"
            data-testid="disposition-abandon"
            @click="dispose('ABANDONED')"
          >
            ABANDONED
          </button>
        </div>
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
button.danger { background: #9b1c1c; border-color: #9b1c1c; }
.actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.dump { background: #f0f4f8; border-radius: 8px; padding: .75rem; overflow: auto; max-height: 320px; font-size: .8rem; }
</style>
