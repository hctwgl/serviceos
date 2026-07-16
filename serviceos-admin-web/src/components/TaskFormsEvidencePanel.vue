<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  authorizeFileDownload,
  beginEvidenceUpload,
  createEvidenceSetSnapshot,
  finalizeEvidenceUpload,
  getEvidenceItem,
  getEvidenceSetSnapshot,
  getFormSubmission,
  invalidateEvidenceRevision,
  invalidateStoredFile,
  listTaskEvidenceItems,
  listTaskEvidenceSlots,
  listTaskForms,
  putAuthorizedUpload,
  resolveEvidenceConditionChange,
  sha256Hex,
  submitTaskForm,
  type DownloadAuthorization,
  type EvidenceItem,
  type EvidenceSetSnapshot,
  type EvidenceSlot,
  type FormSubmission,
  type StoredFile,
  type TaskForm,
} from '../api/formsEvidence'
import { createReviewCase } from '../api/reviews'
import { newIdempotencyKey } from '../api/client'
import QueueTable from '../pages/QueueTable.vue'

const props = defineProps<{ taskId: string }>()
const emit = defineEmits<{ preparedComplete: [payload: { resultRef: string; resultDigest: string }] }>()

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const forms = ref<TaskForm[]>([])
const slots = ref<EvidenceSlot[]>([])
const items = ref<EvidenceItem[]>([])
const valuesJson = ref('{}')
const selectedFormVersionId = ref('')
const revisionIds = ref('')
const lastSubmission = ref<FormSubmission | null>(null)
const lastSnapshot = ref<EvidenceSetSnapshot | null>(null)
const lastReviewCaseId = ref('')
const uploadSlotId = ref('')
const uploadFile = ref<File | null>(null)
const dispositionSlotId = ref('')
const dispositionResolutionId = ref('')
const dispositionDecision = ref<'KEEP' | 'INVALIDATE'>('KEEP')
const dispositionReason = ref('CONDITION_REVIEW')
const dispositionReviewRef = ref('admin-manual-review')
const downloadRevisionId = ref('')
const downloadPurpose = ref('ADMIN_REVIEW')
const lastDownload = ref<DownloadAuthorization | null>(null)
const invalidateRevisionId = ref('')
const invalidateReason = ref('QUALITY_ISSUE')
const lookupSubmissionId = ref('')
const lookupItemId = ref('')
const lookupSnapshotId = ref('')
const lookupJson = ref('')
const invalidateFileId = ref('')
const invalidateFileReason = ref('OPERATOR_INVALIDATE')
const lastInvalidatedFile = ref<StoredFile | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const [formList, slotList, itemList] = await Promise.all([
      listTaskForms(props.taskId),
      listTaskEvidenceSlots(props.taskId),
      listTaskEvidenceItems(props.taskId),
    ])
    forms.value = formList
    slots.value = slotList
    items.value = itemList
    if (!selectedFormVersionId.value && formList[0]) {
      selectedFormVersionId.value = formList[0].formVersionId
    }
    if (!uploadSlotId.value && slotList[0]) {
      uploadSlotId.value = slotList[0].slotId
    }
    const reviewRequired = slotList.find((slot) => slot.requiredDisposition === 'REVIEW_REQUIRED')
    if (reviewRequired) {
      dispositionSlotId.value = reviewRequired.slotId
      dispositionResolutionId.value = reviewRequired.resolutionId
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载表单/资料失败'
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!selectedFormVersionId.value) throw new Error('需要选择 formVersionId')
    const values = JSON.parse(valuesJson.value) as Record<string, unknown>
    lastSubmission.value = (
      await submitTaskForm(props.taskId, {
        formVersionId: selectedFormVersionId.value,
        values,
      })
    ).data
    message.value = `表单提交 ${lastSubmission.value.submissionId} / ${lastSubmission.value.validationStatus}`
    if (lastSubmission.value.validationStatus === 'VALIDATED') {
      emit('preparedComplete', {
        resultRef: `form-submission://${lastSubmission.value.submissionId}`,
        resultDigest: lastSubmission.value.contentDigest,
      })
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '表单提交失败'
  } finally {
    busy.value = false
  }
}

async function uploadEvidence() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!uploadSlotId.value) throw new Error('需要选择 slotId')
    if (!uploadFile.value) throw new Error('需要选择文件')
    const file = uploadFile.value
    const digest = await sha256Hex(file)
    const session = (
      await beginEvidenceUpload(props.taskId, uploadSlotId.value, {
        originalFileName: file.name,
        declaredMimeType: file.type || 'application/octet-stream',
        expectedSize: file.size,
        expectedSha256: digest,
        captureMetadata: {
          capturedAt: new Date().toISOString(),
          captureSource: 'FILE',
          source: 'FILE',
        },
      })
    ).data
    await putAuthorizedUpload(session, file)
    const item = (
      await finalizeEvidenceUpload(props.taskId, uploadSlotId.value, session.uploadSessionId, {
        actualSha256: digest,
        finalizeCommandId: newIdempotencyKey('evidence-finalize'),
      })
    ).data
    const latest = item.revisions[item.revisions.length - 1]
    message.value = `已 Finalize 资料项 ${item.evidenceItemId} / revision ${latest?.evidenceRevisionId ?? '—'}`
    if (latest?.evidenceRevisionId) {
      const current = revisionIds.value.trim()
      revisionIds.value = current
        ? `${current},${latest.evidenceRevisionId}`
        : latest.evidenceRevisionId
      downloadRevisionId.value = latest.evidenceRevisionId
      invalidateRevisionId.value = latest.evidenceRevisionId
    }
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '资料上传失败'
  } finally {
    busy.value = false
  }
}

async function downloadLatest() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const revisionId = downloadRevisionId.value.trim()
    if (!revisionId) throw new Error('需要 revisionId')
    let fileId: string | undefined
    for (const item of items.value) {
      const rev = item.revisions.find((r) => r.evidenceRevisionId === revisionId)
      if (rev?.fileObjectId) {
        fileId = rev.fileObjectId
        break
      }
    }
    if (!fileId) throw new Error('未找到 revision 的 fileObjectId；请先刷新资料项')
    lastDownload.value = (await authorizeFileDownload(fileId, downloadPurpose.value.trim())).data
    message.value = `已签发下载授权 ${lastDownload.value.authorizationId}，expires ${lastDownload.value.expiresAt}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '申请下载授权失败'
  } finally {
    busy.value = false
  }
}

async function invalidateRevision() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!invalidateRevisionId.value.trim()) throw new Error('需要 revisionId')
    const result = await invalidateEvidenceRevision(invalidateRevisionId.value.trim(), {
      reasonCode: invalidateReason.value.trim(),
    })
    message.value = `已作废资料版本 ${result.data.evidenceRevisionId} / ${result.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '作废资料版本失败'
  } finally {
    busy.value = false
  }
}

async function lookupSubmission() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!lookupSubmissionId.value.trim()) throw new Error('需要 submissionId')
    const data = await getFormSubmission(lookupSubmissionId.value.trim())
    lookupJson.value = JSON.stringify(data, null, 2)
    message.value = `已加载表单提交 ${data.submissionId}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载表单提交失败'
  } finally {
    busy.value = false
  }
}

async function lookupItem() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!lookupItemId.value.trim()) throw new Error('需要 evidenceItemId')
    const data = await getEvidenceItem(lookupItemId.value.trim())
    lookupJson.value = JSON.stringify(data, null, 2)
    message.value = `已加载资料项 ${data.evidenceItemId}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载资料项失败'
  } finally {
    busy.value = false
  }
}

async function lookupSnapshot() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!lookupSnapshotId.value.trim()) throw new Error('需要 snapshotId')
    const data = await getEvidenceSetSnapshot(lookupSnapshotId.value.trim())
    lookupJson.value = JSON.stringify(data, null, 2)
    message.value = `已加载资料快照 ${data.evidenceSetSnapshotId}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载资料快照失败'
  } finally {
    busy.value = false
  }
}

async function invalidateFile() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!invalidateFileId.value.trim()) throw new Error('需要 fileId')
    lastInvalidatedFile.value = (
      await invalidateStoredFile(invalidateFileId.value.trim(), {
        reasonCode: invalidateFileReason.value.trim(),
        sourceType: 'ADMIN_TASK',
        sourceId: props.taskId,
      })
    ).data
    message.value = `已作废文件 ${lastInvalidatedFile.value.fileId} / ${lastInvalidatedFile.value.lifecycleStatus}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '作废文件失败'
  } finally {
    busy.value = false
  }
}

async function disposeCondition() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!dispositionSlotId.value || !dispositionResolutionId.value) {
      throw new Error('需要 slotId 与 expectedResolutionId')
    }
    const result = await resolveEvidenceConditionChange(props.taskId, dispositionSlotId.value, {
      expectedResolutionId: dispositionResolutionId.value,
      decision: dispositionDecision.value,
      reasonCode: dispositionReason.value.trim(),
      reviewRef: dispositionReviewRef.value.trim(),
    })
    message.value = `条件处置 ${result.data.decision} / ${result.data.dispositionId}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '条件处置失败'
  } finally {
    busy.value = false
  }
}

async function createSnapshot() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const ids = revisionIds.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    if (ids.length === 0) throw new Error('需要至少一个 VALIDATED revisionId')
    lastSnapshot.value = (await createEvidenceSetSnapshot(props.taskId, ids)).data
    message.value = `资料快照 ${lastSnapshot.value.evidenceSetSnapshotId}`
    emit('preparedComplete', {
      resultRef: `evidence-set-snapshot://${lastSnapshot.value.evidenceSetSnapshotId}`,
      resultDigest: lastSnapshot.value.contentDigest,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建快照失败'
  } finally {
    busy.value = false
  }
}

async function openReview() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!lastSnapshot.value) throw new Error('需要先创建资料快照')
    const created = await createReviewCase({
      evidenceSetSnapshotId: lastSnapshot.value.evidenceSetSnapshotId,
    })
    lastReviewCaseId.value = created.data.reviewCaseId
    message.value = `已创建审核案例 ${created.data.reviewCaseId}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建审核案例失败'
  } finally {
    busy.value = false
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
}

const formRows = computed(() =>
  forms.value.map((item) => ({
    formKey: item.formKey,
    formVersionId: item.formVersionId,
    semanticVersion: item.semanticVersion,
    contentDigest: item.contentDigest,
  })),
)
const slotRows = computed(() =>
  slots.value.map((item) => ({
    slotId: item.slotId,
    requirementCode: item.requirementCode,
    requirementName: item.requirementName,
    mediaType: item.mediaType,
    required: item.required,
    status: item.status,
    active: item.active,
    requiredDisposition: item.requiredDisposition,
    resolutionId: item.resolutionId,
  })),
)
const itemRows = computed(() =>
  items.value.map((item) => {
    const latest = item.revisions[item.revisions.length - 1]
    return {
      evidenceItemId: item.evidenceItemId,
      evidenceSlotId: item.evidenceSlotId,
      status: item.status,
      latestRevisionId: latest?.evidenceRevisionId,
      latestRevisionStatus: latest?.status,
      latestDigest: latest?.contentDigest,
      fileObjectId: latest?.fileObjectId,
    }
  }),
)

watch(
  () => props.taskId,
  () => {
    void load()
  },
)
onMounted(() => {
  void load()
})
</script>

<template>
  <section class="panel">
    <header class="top">
      <h3>表单 / 资料编排</h3>
      <button type="button" :disabled="loading || busy" @click="load">刷新</button>
    </header>
    <p class="meta">
      含表单提交、Begin→PUT→Finalize 上传、条件处置、资料快照与创建审核案例；并可回填 complete 引用。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-else-if="loading">加载中…</p>

    <QueueTable
      title="锁定表单"
      :columns="['formKey', 'formVersionId', 'semanticVersion', 'contentDigest']"
      :rows="formRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article v-if="forms.length" class="card">
      <h4>提交表单</h4>
      <label>
        formVersionId
        <select v-model="selectedFormVersionId">
          <option v-for="form in forms" :key="form.formVersionId" :value="form.formVersionId">
            {{ form.formKey }} @ {{ form.semanticVersion }}
          </option>
        </select>
      </label>
      <label>
        values JSON
        <textarea v-model="valuesJson" rows="6" />
      </label>
      <button type="button" :disabled="busy" @click="submitForm">submitTaskForm</button>
      <p v-if="lastSubmission" class="meta">
        last={{ lastSubmission.submissionId }} / {{ lastSubmission.validationStatus }} /
        digest={{ lastSubmission.contentDigest }}
      </p>
    </article>

    <QueueTable
      title="资料槽位"
      :columns="['slotId', 'requirementCode', 'requirementName', 'mediaType', 'required', 'status', 'active', 'requiredDisposition', 'resolutionId']"
      :rows="slotRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article v-if="slots.length" class="card">
      <h4>资料上传 Begin → PUT → Finalize</h4>
      <label>
        slotId
        <select v-model="uploadSlotId">
          <option v-for="slot in slots" :key="slot.slotId" :value="slot.slotId">
            {{ slot.requirementCode }} / {{ slot.slotId }}
          </option>
        </select>
      </label>
      <label>
        文件
        <input type="file" @change="onFileChange" />
      </label>
      <button type="button" :disabled="busy" @click="uploadEvidence">upload + finalize</button>
    </article>

    <article v-if="slots.some((s) => s.requiredDisposition === 'REVIEW_REQUIRED')" class="card">
      <h4>条件变化处置</h4>
      <label>slotId<input v-model="dispositionSlotId" /></label>
      <label>expectedResolutionId<input v-model="dispositionResolutionId" /></label>
      <label>
        decision
        <select v-model="dispositionDecision">
          <option value="KEEP">KEEP</option>
          <option value="INVALIDATE">INVALIDATE</option>
        </select>
      </label>
      <label>reasonCode<input v-model="dispositionReason" /></label>
      <label>reviewRef<input v-model="dispositionReviewRef" /></label>
      <button type="button" :disabled="busy" @click="disposeCondition">resolve-condition-change</button>
    </article>

    <QueueTable
      title="资料项 / 最新 Revision"
      :columns="['evidenceItemId', 'evidenceSlotId', 'status', 'latestRevisionId', 'latestRevisionStatus', 'latestDigest', 'fileObjectId']"
      :rows="itemRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article class="card">
      <h4>资料下载授权 / 作废 Revision</h4>
      <label>revisionId（下载）<input v-model="downloadRevisionId" /></label>
      <label>purpose<input v-model="downloadPurpose" /></label>
      <button type="button" :disabled="busy" @click="downloadLatest">authorizeFileDownload</button>
      <p v-if="lastDownload" class="meta">
        auth={{ lastDownload.authorizationId }} / url={{ lastDownload.downloadUrl }} /
        expires={{ lastDownload.expiresAt }}
      </p>
      <label>revisionId（作废）<input v-model="invalidateRevisionId" /></label>
      <label>reasonCode<input v-model="invalidateReason" /></label>
      <button type="button" :disabled="busy" @click="invalidateRevision">invalidateEvidenceRevision</button>
      <label>fileId（作废 StoredFile）<input v-model="invalidateFileId" /></label>
      <label>file reasonCode<input v-model="invalidateFileReason" /></label>
      <button type="button" :disabled="busy" @click="invalidateFile">invalidateStoredFile</button>
      <p v-if="lastInvalidatedFile" class="meta">
        file={{ lastInvalidatedFile.fileId }} / {{ lastInvalidatedFile.lifecycleStatus }}
      </p>
    </article>

    <article class="card">
      <h4>按 ID 读取提交 / 资料项 / 快照</h4>
      <label>submissionId<input v-model="lookupSubmissionId" /></label>
      <button type="button" :disabled="busy" @click="lookupSubmission">getFormSubmission</button>
      <label>evidenceItemId<input v-model="lookupItemId" /></label>
      <button type="button" :disabled="busy" @click="lookupItem">getEvidenceItem</button>
      <label>snapshotId<input v-model="lookupSnapshotId" /></label>
      <button type="button" :disabled="busy" @click="lookupSnapshot">getEvidenceSetSnapshot</button>
      <pre v-if="lookupJson" class="preview">{{ lookupJson }}</pre>
    </article>

    <article class="card">
      <h4>创建资料快照 / 审核案例</h4>
      <label>
        memberRevisionIds（逗号分隔 VALIDATED revision）
        <input v-model="revisionIds" placeholder="uuid,uuid" />
      </label>
      <div class="actions">
        <button type="button" :disabled="busy" @click="createSnapshot">createEvidenceSetSnapshot</button>
        <button type="button" :disabled="busy || !lastSnapshot" @click="openReview">createReviewCase</button>
      </div>
      <p v-if="lastSnapshot" class="meta">
        snapshot={{ lastSnapshot.evidenceSetSnapshotId }} / digest={{ lastSnapshot.contentDigest }}
      </p>
      <p v-if="lastReviewCaseId" class="links">
        <RouterLink :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: lastReviewCaseId } }">
          打开审核案例 {{ lastReviewCaseId }}
        </RouterLink>
      </p>
    </article>
  </section>
</template>

<style scoped>
.panel { display: grid; gap: 1rem; }
.top { display: flex; justify-content: space-between; align-items: center; }
.top h3 { margin: 0; }
.meta { margin: 0; color: #627d98; font-size: .85rem; word-break: break-all; }
.card { background: #fff; border-radius: 12px; padding: 1rem 1.15rem; box-shadow: 0 1px 3px rgb(16 42 67 / 8%); display: grid; gap: .55rem; }
label { display: grid; gap: .25rem; font-size: .85rem; color: #486581; }
input, select, textarea, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; font-family: ui-monospace, monospace; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; font-family: inherit; }
.actions { display: flex; gap: .5rem; flex-wrap: wrap; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
.links { margin: 0; }
.preview {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  background: #f0f4f8;
  border-radius: 8px;
  padding: .65rem;
  font-size: .78rem;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
