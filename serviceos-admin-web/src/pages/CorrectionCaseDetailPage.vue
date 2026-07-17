<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  closeCorrectionCase,
  getCorrectionCase,
  resubmitCorrectionCase,
  waiveCorrectionCase,
  type CorrectionCase,
} from '../api/corrections'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const correctionCaseId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<CorrectionCase | null>(null)
const snapshotId = ref('')
const closeNote = ref('')
const waiveReason = ref('')
const approvalRef = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getCorrectionCase(correctionCaseId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载整改案例失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function resubmit() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!/^[0-9a-fA-F-]{36}$/.test(snapshotId.value.trim())) {
      throw new Error('需要有效的 evidenceSetSnapshotId')
    }
    const resubmitted = (
      await resubmitCorrectionCase(correctionCaseId.value, snapshotId.value.trim())
    ).data
    // 命令成功后重新读取权威详情，避免补传轮次与 Case 投影停留在命令响应快照。
    await load()
    message.value = `已补传，status=${resubmitted.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '补传失败'
  } finally {
    busy.value = false
  }
}

async function closeCase() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const closed = (
      await closeCorrectionCase(correctionCaseId.value, closeNote.value || undefined)
    ).data
    await load()
    message.value = `已关闭，status=${closed.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '关闭失败'
  } finally {
    busy.value = false
  }
}

async function waive() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!waiveReason.value.trim() || !approvalRef.value.trim()) {
      throw new Error('豁免需要 reason 与 approvalRef')
    }
    const waived = (
      await waiveCorrectionCase(correctionCaseId.value, {
        reason: waiveReason.value.trim(),
        approvalRef: approvalRef.value.trim(),
      })
    ).data
    // 豁免会在同一事务取消整改 Task；重新读取详情后继续展示权威 Case，
    // 成功提示不能遮蔽 status、任务引用和补传历史。
    await load()
    message.value = `已豁免，status=${waived.status}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '豁免失败'
  } finally {
    busy.value = false
  }
}

const resubmissionRows = computed(() =>
  (detail.value?.resubmissions ?? []).map((item) => ({
    ordinal: item.resubmissionOrdinal,
    evidenceSetSnapshotId: item.evidenceSetSnapshotId,
    snapshotContentDigest: item.snapshotContentDigest,
    submittedAt: item.submittedAt,
  })),
)

watch(correctionCaseId, () => {
  if (correctionCaseId.value) void load()
})
onMounted(() => {
  if (correctionCaseId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>整改案例</h2>
        <p class="meta">{{ correctionCaseId }}</p>
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
            <dt>taskId</dt>
            <dd>
              <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
                {{ detail.taskId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>correctionTaskId</dt>
            <dd>
              <RouterLink
                v-if="detail.correctionTaskId"
                :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.correctionTaskId } }"
              >
                {{ detail.correctionTaskId }}
              </RouterLink>
              <template v-else>-</template>
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
            <dt>sourceEvidenceSetSnapshotId</dt>
            <dd>
              <RouterLink
                :to="{
                  name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
                  params: { id: detail.sourceEvidenceSetSnapshotId },
                }"
              >
                {{ detail.sourceEvidenceSetSnapshotId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>latestResubmissionSnapshotId</dt>
            <dd>
              <RouterLink
                v-if="detail.latestResubmissionSnapshotId"
                :to="{
                  name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
                  params: { id: detail.latestResubmissionSnapshotId },
                }"
              >
                {{ detail.latestResubmissionSnapshotId }}
              </RouterLink>
              <template v-else>-</template>
            </dd>
          </div>
          <div><dt>reasonCodes</dt><dd>{{ detail.reasonCodes.join(', ') }}</dd></div>
        </dl>
        <p class="links correction-cross-links">
          <RouterLink
            :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
          >
            打开项目 {{ detail.projectId }}
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.sourceReviewCaseId } }">
            源审核案例
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            来源任务
          </RouterLink>
          <RouterLink
            v-if="detail.correctionTaskId"
            :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.correctionTaskId } }"
          >
            整改任务
          </RouterLink>
          <RouterLink
            :to="{
              name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
              params: { id: detail.sourceEvidenceSetSnapshotId },
            }"
          >
            打开源资料快照 {{ detail.sourceEvidenceSetSnapshotId }}
          </RouterLink>
          <RouterLink
            v-if="detail.latestResubmissionSnapshotId"
            :to="{
              name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
              params: { id: detail.latestResubmissionSnapshotId },
            }"
          >
            打开最近补传快照 {{ detail.latestResubmissionSnapshotId }}
          </RouterLink>
        </p>
      </article>

      <article class="card">
        <h3>命令</h3>
        <label>resubmit snapshotId
          <input v-model="snapshotId" placeholder="evidenceSetSnapshotId" />
        </label>
        <label>close note
          <input v-model="closeNote" />
        </label>
        <label>waive reason
          <input v-model="waiveReason" />
        </label>
        <label>waive approvalRef
          <input v-model="approvalRef" />
        </label>
        <div class="actions">
          <button type="button" :disabled="busy" @click="resubmit">resubmit</button>
          <button type="button" :disabled="busy" @click="closeCase">close</button>
          <button type="button" :disabled="busy" @click="waive">waive</button>
        </div>
      </article>

      <QueueTable
        title="补传轮次"
        :columns="['ordinal', 'evidenceSetSnapshotId', 'snapshotContentDigest', 'submittedAt']"
        :rows="resubmissionRows"
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
input, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; }
.actions { display: flex; gap: .5rem; flex-wrap: wrap; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
.links { display: flex; gap: .75rem; margin: .5rem 0 0; }
</style>
