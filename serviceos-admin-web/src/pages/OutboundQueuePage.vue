<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listOutboundDeliveries,
  type OutboundDeliveryQueuePage,
  type OutboundDeliveryQueueQuery,
} from '../api/queues'
import {
  approveBatchReplayRequest,
  createBatchReplayRequest,
  getBatchReplayRequest,
  type BatchReplayRequest,
} from '../api/batchReplay'
import { hasCapability, loadActiveCapabilityCodes } from '../api/capabilitiesGate'
import { firstRouteQuery, uuidRoute } from '../routeQuery'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  deliveryId: (row) => uuidRoute(row.deliveryId, 'ADMIN.INTEGRATION.DETAIL'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
  workspace: (row) => uuidRoute(row.workspace, 'ADMIN.WORKORDER.WORKSPACE'),
}

const route = useRoute()

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<OutboundDeliveryQueuePage | null>(null)
const cursor = ref<string | undefined>()
const selectedIds = ref<string[]>([])
const batchReason = ref('')
const batchApprovalRef = ref('')
const batch = ref<BatchReplayRequest | null>(null)
const decisionNote = ref('')
const capabilityCodes = ref<string[]>([])

/** 与 OpenAPI 默认一致：省略或空时服务端仍按 UNKNOWN。显式 query 可覆盖。 */
const status = ref('UNKNOWN')
const businessMessageType = ref('')
const projectId = ref('')
const sourceWorkOrderId = ref('')
const sourceReviewCaseId = ref('')

const canBatchReplay = computed(
  () =>
    capabilityCodes.value.length === 0 ||
    hasCapability(capabilityCodes.value, 'integration.batchReplayUnknownDelivery'),
)
const canApproveBatch = computed(
  () =>
    capabilityCodes.value.length === 0 ||
    (hasCapability(capabilityCodes.value, 'integration.batchReplayUnknownDelivery') &&
      hasCapability(capabilityCodes.value, 'integration.retryUnknownDelivery')),
)

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextBusinessMessageType = firstRouteQuery(route, 'businessMessageType')
  if (nextBusinessMessageType !== undefined) {
    businessMessageType.value = nextBusinessMessageType
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  const nextSourceWorkOrderId = firstRouteQuery(route, 'sourceWorkOrderId')
  if (nextSourceWorkOrderId !== undefined) {
    sourceWorkOrderId.value = nextSourceWorkOrderId
  }
  const nextSourceReviewCaseId = firstRouteQuery(route, 'sourceReviewCaseId')
  if (nextSourceReviewCaseId !== undefined) {
    sourceReviewCaseId.value = nextSourceReviewCaseId
  }
}

function queryParams(next?: string): OutboundDeliveryQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    businessMessageType: businessMessageType.value || undefined,
    projectId: projectId.value.trim() || undefined,
    sourceWorkOrderId: sourceWorkOrderId.value.trim() || undefined,
    sourceReviewCaseId: sourceReviewCaseId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listOutboundDeliveries(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载外发队列失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    ...item,
    workspace: item.sourceWorkOrderId,
  })),
)

function toggleSelected(deliveryId: string, checked: boolean) {
  if (checked) {
    if (selectedIds.value.includes(deliveryId)) return
    if (selectedIds.value.length >= 20) {
      error.value = '批量选择上限 20 条'
      return
    }
    selectedIds.value = [...selectedIds.value, deliveryId]
    return
  }
  selectedIds.value = selectedIds.value.filter((id) => id !== deliveryId)
}

function selectAllVisible() {
  const ids = (page.value?.items ?? [])
    .filter((item) => item.status === 'UNKNOWN')
    .map((item) => item.deliveryId)
    .slice(0, 20)
  selectedIds.value = ids
}

async function runBatch(mode: 'PREVIEW' | 'SUBMIT') {
  busy.value = true
  error.value = null
  message.value = null
  try {
    if (selectedIds.value.length === 0) throw new Error('请先选择 UNKNOWN 交付')
    if (!batchReason.value.trim()) throw new Error('需要 reason')
    if (mode === 'SUBMIT' && !batchApprovalRef.value.trim()) {
      throw new Error('SUBMIT 需要 approvalRef')
    }
    const result = await createBatchReplayRequest({
      deliveryIds: selectedIds.value,
      mode,
      reason: batchReason.value.trim(),
      approvalRef: mode === 'SUBMIT' ? batchApprovalRef.value.trim() : null,
      maxItems: 20,
    })
    batch.value = result.data
    message.value = `批量 ${mode} 完成：${result.data.batchId} / ${result.data.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '批量重放失败'
  } finally {
    busy.value = false
  }
}

async function refreshBatch() {
  if (!batch.value) return
  busy.value = true
  error.value = null
  try {
    batch.value = await getBatchReplayRequest(batch.value.batchId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '刷新批次失败'
  } finally {
    busy.value = false
  }
}

async function decideBatch(decision: 'APPROVE' | 'REJECT') {
  busy.value = true
  error.value = null
  message.value = null
  try {
    if (!batch.value) throw new Error('尚无批次')
    if (batch.value.status !== 'PENDING_APPROVAL') {
      throw new Error('仅 PENDING_APPROVAL 可审批')
    }
    const result = await approveBatchReplayRequest(batch.value.batchId, {
      decision,
      decisionNote: decisionNote.value.trim() || null,
      maxItems: 20,
    })
    batch.value = result.data
    message.value = `批次已 ${decision}：${result.data.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '批次审批失败'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  hydrateFiltersFromRoute()
  void loadActiveCapabilityCodes().then((codes) => {
    capabilityCodes.value = codes
  })
  return load()
})
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="outbound status filter">
          <option value="UNKNOWN">UNKNOWN</option>
          <option value="PENDING">PENDING</option>
          <option value="SENDING">SENDING</option>
          <option value="DELIVERED">DELIVERED</option>
          <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="FAILED_FINAL">FAILED_FINAL</option>
        </select>
      </label>
      <label>
        businessMessageType
        <select v-model="businessMessageType" aria-label="outbound businessMessageType filter">
          <option value="">（不限）</option>
          <option value="SUBMIT_CLIENT_REVIEW">SUBMIT_CLIENT_REVIEW</option>
        </select>
      </label>
      <label>
        projectId
        <input
          v-model="projectId"
          aria-label="outbound projectId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        sourceWorkOrderId
        <input
          v-model="sourceWorkOrderId"
          aria-label="outbound sourceWorkOrderId filter"
          placeholder="uuid"
        />
      </label>
      <label>
        sourceReviewCaseId
        <input
          v-model="sourceReviewCaseId"
          aria-label="outbound sourceReviewCaseId filter"
          placeholder="uuid"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <p v-if="message" class="ok">{{ message }}</p>

    <QueueTable
      title="外发交付队列"
      :columns="['deliveryId', 'projectId', 'status', 'externalOrderCode', 'attemptCount', 'createdAt', 'workspace']"
      :rows="rows"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />

    <article v-if="canBatchReplay && page?.items?.length" class="batch card" data-testid="batch-replay-card">
      <h3>批量 UNKNOWN Replay</h3>
      <p class="hint">选择当前页 UNKNOWN 交付（最多 20），先 PREVIEW 再 SUBMIT；审批需 retry 能力。</p>
      <div class="select-list">
        <label v-for="item in page.items.filter((row) => row.status === 'UNKNOWN')" :key="item.deliveryId" class="check">
          <input
            type="checkbox"
            :checked="selectedIds.includes(item.deliveryId)"
            :data-testid="`batch-select-${item.deliveryId}`"
            @change="toggleSelected(item.deliveryId, ($event.target as HTMLInputElement).checked)"
          />
          {{ item.externalOrderCode || item.deliveryId }}
        </label>
      </div>
      <div class="batch-actions">
        <button type="button" :disabled="busy" data-testid="batch-select-all" @click="selectAllVisible">
          全选本页 UNKNOWN
        </button>
        <label>reason<input v-model="batchReason" data-testid="batch-reason" /></label>
        <label>approvalRef（SUBMIT）<input v-model="batchApprovalRef" data-testid="batch-approval-ref" /></label>
        <button type="button" :disabled="busy" data-testid="batch-preview" @click="runBatch('PREVIEW')">
          PREVIEW
        </button>
        <button type="button" :disabled="busy" data-testid="batch-submit" @click="runBatch('SUBMIT')">
          SUBMIT
        </button>
      </div>
      <template v-if="batch">
        <p class="hint">
          batchId={{ batch.batchId }} status={{ batch.status }} mode={{ batch.mode }}
          <button type="button" :disabled="busy" @click="refreshBatch">刷新批次</button>
        </p>
        <pre class="dump" data-testid="batch-items">{{ JSON.stringify(batch.items, null, 2) }}</pre>
        <div v-if="batch.status === 'PENDING_APPROVAL'" class="batch-actions">
          <label>decisionNote<input v-model="decisionNote" data-testid="batch-decision-note" /></label>
          <button
            type="button"
            :disabled="busy || !canApproveBatch"
            data-testid="batch-approve"
            @click="decideBatch('APPROVE')"
          >
            APPROVE
          </button>
          <button
            type="button"
            class="danger"
            :disabled="busy || !canBatchReplay"
            data-testid="batch-reject"
            @click="decideBatch('REJECT')"
          >
            REJECT
          </button>
        </div>
      </template>
    </article>

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
    <p v-if="page?.items?.length" class="links outbound-queue-cross-links">
      打开关联资源：
      <RouterLink
        v-for="item in page.items"
        :key="`project-${item.deliveryId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
      >
        打开项目 {{ item.projectId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items"
        :key="`src-review-${item.deliveryId}`"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.sourceReviewCaseId } }"
      >
        打开源审核 {{ item.sourceReviewCaseId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items"
        :key="`src-task-${item.deliveryId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.sourceTaskId } }"
      >
        打开源任务 {{ item.sourceTaskId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items"
        :key="`src-snap-${item.deliveryId}`"
        :to="{
          name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
          params: { id: item.sourceSnapshotId },
        }"
      >
        打开源资料快照 {{ item.sourceSnapshotId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.executionTaskId)"
        :key="`exec-${item.deliveryId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.executionTaskId! } }"
      >
        打开执行任务 {{ item.executionTaskId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.clientReviewCaseId)"
        :key="`client-${item.deliveryId}`"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.clientReviewCaseId! } }"
      >
        打开 CLIENT 审核 {{ item.clientReviewCaseId }}
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
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
  margin: 1rem 0;
  display: grid;
  gap: 0.65rem;
}
.hint { margin: 0; color: #627d98; font-size: 0.85rem; }
.select-list { display: grid; gap: 0.35rem; max-height: 220px; overflow: auto; }
.check { display: flex; gap: 0.5rem; align-items: center; font-size: 0.9rem; }
.batch-actions { display: flex; flex-wrap: wrap; gap: 0.65rem; align-items: end; }
button.danger { background: #9b1c1c; border-color: #9b1c1c; }
.ok { color: #054e31; }
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  max-height: 280px;
  font-size: 0.8rem;
}
</style>
