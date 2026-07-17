<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getNetworkPortalCorrection,
  type NetworkPortalCorrectionDetail,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const correctionCaseId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalCorrectionDetail | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!correctionCaseId.value) {
    detail.value = null
    error.value = '缺少 correctionCaseId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalCorrection(
      props.networkContextId,
      correctionCaseId.value,
    )
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '整改详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, correctionCaseId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-correction-detail"
    data-page-id="NETWORK.CORRECTION.QUEUE"
  >
    <header class="top">
      <div>
        <h2>整改详情</h2>
        <p class="meta" data-testid="correction-detail-id">{{ correctionCaseId }}</p>
      </div>
      <div class="actions">
        <RouterLink to="/network-portal/corrections" data-testid="correction-back-to-queue">
          返回队列
        </RouterLink>
        <button type="button" :disabled="loading" data-testid="correction-detail-refresh" @click="load">
          刷新
        </button>
      </div>
    </header>
    <p class="hint">只读详情（复用 M202 GET）；写操作请经任务页资料代补，不提供 close/waive。</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="correction-detail-loading">加载中…</p>
    <template v-else-if="detail">
      <dl data-testid="correction-detail-fields">
        <div><dt>status</dt><dd data-testid="correction-detail-status">{{ detail.status }}</dd></div>
        <div><dt>projectId</dt><dd>{{ detail.projectId }}</dd></div>
        <div>
          <dt>taskId</dt>
          <dd>
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: detail.taskId } }"
              data-testid="correction-detail-task-deeplink"
            >
              {{ detail.taskId }}
            </RouterLink>
          </dd>
        </div>
        <div><dt>sourceReviewCaseId</dt><dd>{{ detail.sourceReviewCaseId }}</dd></div>
        <div><dt>sourceReviewDecisionId</dt><dd>{{ detail.sourceReviewDecisionId }}</dd></div>
        <div>
          <dt>sourceEvidenceSetSnapshotId</dt>
          <dd data-testid="correction-detail-source-snapshot">
            {{ detail.sourceEvidenceSetSnapshotId }}
          </dd>
        </div>
        <div>
          <dt>sourceSnapshotContentDigest</dt>
          <dd>{{ detail.sourceSnapshotContentDigest }}</dd>
        </div>
        <div>
          <dt>reasonCodes</dt>
          <dd>{{ detail.reasonCodes.join(', ') || '—' }}</dd>
        </div>
        <div><dt>correctionTaskId</dt><dd>{{ detail.correctionTaskId ?? '—' }}</dd></div>
        <div><dt>createdBy</dt><dd>{{ detail.createdBy }}</dd></div>
        <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
        <div>
          <dt>latestResubmissionSnapshotId</dt>
          <dd>{{ detail.latestResubmissionSnapshotId ?? '—' }}</dd>
        </div>
        <div><dt>closedAt</dt><dd>{{ detail.closedAt ?? '—' }}</dd></div>
        <div><dt>waivedAt</dt><dd>{{ detail.waivedAt ?? '—' }}</dd></div>
      </dl>

      <h3>补传历史</h3>
      <table data-testid="correction-resubmissions-table">
        <thead>
          <tr>
            <th>#</th>
            <th>snapshotId</th>
            <th>digest</th>
            <th>submittedAt</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in detail.resubmissions"
            :key="item.correctionResubmissionId"
            :data-testid="`correction-resubmission-${item.resubmissionOrdinal}`"
          >
            <td>{{ item.resubmissionOrdinal }}</td>
            <td>{{ item.evidenceSetSnapshotId }}</td>
            <td>{{ item.snapshotContentDigest }}</td>
            <td>{{ item.submittedAt ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p
        v-if="detail.resubmissions.length === 0"
        data-testid="correction-resubmissions-empty"
      >
        尚无补传轮次
      </p>
    </template>
  </section>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.meta,
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
dl {
  display: grid;
  gap: 0.35rem;
  margin: 1rem 0;
}
dt {
  font-size: 0.75rem;
  color: #5b6573;
}
dd {
  margin: 0 0 0.35rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.45rem 0.35rem;
  text-align: left;
  font-size: 0.85rem;
}
</style>
