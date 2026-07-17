<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  decideReviewCase,
  forceApproveReviewCase,
  getReviewCase,
  reopenReviewCase,
  type ReviewCase,
} from '../api/reviews'
import { createBydReviewSubmission } from '../api/integrationCommands'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const router = useRouter()
const reviewCaseId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<ReviewCase | null>(null)
const decision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reasonCodes = ref('')
const note = ref('')
const approvalRef = ref('')
const reopenReason = ref('')
const triggerRef = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getReviewCase(reviewCaseId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载审核案例失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

function codes() {
  return reasonCodes.value
    .split(/[,\s]+/)
    .map((c) => c.trim())
    .filter(Boolean)
}

async function decide() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const body = {
      decision: decision.value,
      reasonCodes: codes(),
      note: note.value || null,
    }
    if (decision.value === 'REJECTED' && body.reasonCodes.length === 0) {
      throw new Error('REJECTED 至少需要一个 reasonCode')
    }
    const decided = (await decideReviewCase(reviewCaseId.value, body)).data
    // 裁决命令只负责追加不可变决定；随后重新读取权威详情，避免命令响应与队列/详情投影
    // 在字段演进或异步扩展时让页面残留半更新状态。
    await load()
    message.value = `已裁决为 ${decided.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '裁决失败'
  } finally {
    busy.value = false
  }
}

async function forceApprove() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const reason = codes()
    if (reason.length === 0 || !approvalRef.value.trim()) {
      throw new Error('强制通过需要 reasonCodes 与 approvalRef')
    }
    detail.value = (
      await forceApproveReviewCase(reviewCaseId.value, {
        reasonCodes: reason,
        approvalRef: approvalRef.value.trim(),
        note: note.value || null,
      })
    ).data
    message.value = `已强制通过：${detail.value.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '强制通过失败'
  } finally {
    busy.value = false
  }
}

async function reopen() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!reopenReason.value.trim() || !triggerRef.value.trim()) {
      throw new Error('重开需要 reason 与 triggerRef')
    }
    const reopened = (
      await reopenReviewCase(reviewCaseId.value, {
        reason: reopenReason.value.trim(),
        triggerRef: triggerRef.value.trim(),
        approvalRef: approvalRef.value.trim() || null,
      })
    ).data
    // reopen 返回同 Snapshot 的新 OPEN Case。路由必须切换到新 Case，否则刷新会回到
    // 已标记 REOPENED 的旧案例，形成“页面内容与 URL 身份不一致”的操作风险。
    await router.replace({
      name: 'ADMIN.REVIEW.DETAIL',
      params: { id: reopened.reviewCaseId },
    })
    await load()
    message.value = `重开结果：${reopened.status} / ${reopened.reviewCaseId}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '重开失败'
  } finally {
    busy.value = false
  }
}

async function submitOutbound() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const result = await createBydReviewSubmission(reviewCaseId.value)
    message.value = `已创建外发交付 ${result.data.deliveryId} / ${result.data.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建提审外发失败'
  } finally {
    busy.value = false
  }
}

const decisionRows = computed(() =>
  (detail.value?.decisions ?? []).map((item) => ({
    ordinal: item.decisionOrdinal,
    decision: item.decision,
    reasonCodes: item.reasonCodes.join(', '),
    decidedBy: item.decidedBy,
    decidedAt: item.decidedAt,
  })),
)

watch(reviewCaseId, () => {
  if (reviewCaseId.value) void load()
})
onMounted(() => {
  if (reviewCaseId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>审核案例</h2>
        <p class="meta">{{ reviewCaseId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.status }}</dd></div>
          <div><dt>origin</dt><dd>{{ detail.origin }}</dd></div>
          <div><dt>taskId</dt><dd>{{ detail.taskId }}</dd></div>
          <div><dt>projectId</dt><dd>{{ detail.projectId }}</dd></div>
          <div><dt>snapshot</dt><dd>{{ detail.evidenceSetSnapshotId }}</dd></div>
          <div><dt>reopenedFromReviewCaseId</dt><dd>{{ detail.reopenedFromReviewCaseId ?? '-' }}</dd></div>
          <div><dt>reopenTriggerRef</dt><dd>{{ detail.reopenTriggerRef ?? '-' }}</dd></div>
        </dl>
        <p class="links review-cross-links">
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
          <RouterLink
            :to="{
              name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
              params: { id: detail.evidenceSetSnapshotId },
            }"
          >
            打开资料快照 {{ detail.evidenceSetSnapshotId }}
          </RouterLink>
          <RouterLink
            v-if="detail.reopenedFromReviewCaseId"
            :to="{
              name: 'ADMIN.REVIEW.DETAIL',
              params: { id: detail.reopenedFromReviewCaseId },
            }"
          >
            打开源审核案例 {{ detail.reopenedFromReviewCaseId }}
          </RouterLink>
        </p>
      </article>

      <article v-if="detail.status === 'OPEN'" class="card">
        <h3>裁决</h3>
        <label>decision
          <select v-model="decision">
            <option value="APPROVED">APPROVED</option>
            <option value="REJECTED">REJECTED</option>
          </select>
        </label>
        <label>reasonCodes（逗号分隔）
          <input v-model="reasonCodes" placeholder="IMAGE.BLUR,MISSING_PHOTO" />
        </label>
        <label>note
          <input v-model="note" />
        </label>
        <label>approvalRef（强制通过）
          <input v-model="approvalRef" />
        </label>
        <div class="actions">
          <button type="button" :disabled="busy" @click="decide">decide</button>
          <button type="button" :disabled="busy" @click="forceApprove">force-approve</button>
        </div>
      </article>

      <article v-if="detail.status === 'APPROVED' || detail.status === 'FORCE_APPROVED'" class="card">
        <h3>重开 / 提审外发</h3>
        <label>reason<input v-model="reopenReason" /></label>
        <label>triggerRef<input v-model="triggerRef" /></label>
        <label>approvalRef（可选）<input v-model="approvalRef" /></label>
        <div class="actions">
          <button type="button" :disabled="busy" @click="reopen">reopen</button>
          <button type="button" :disabled="busy" @click="submitOutbound">create BYD review submission</button>
        </div>
      </article>

      <QueueTable
        title="决定历史"
        :columns="['ordinal', 'decision', 'reasonCodes', 'decidedBy', 'decidedAt']"
        :rows="decisionRows"
        :loading="false"
        :error="null"
        :next-cursor="null"
        @refresh="load"
        @next="() => undefined"
      />
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
input, select, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; }
.actions { display: flex; gap: .5rem; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
.links { margin: .5rem 0 0; }
</style>
