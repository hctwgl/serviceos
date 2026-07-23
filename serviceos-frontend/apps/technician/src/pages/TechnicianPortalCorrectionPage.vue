<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  beginTechnicianCorrectionEvidenceUpload,
  claimTechnicianCorrection,
  createTechnicianCorrectionEvidenceSetSnapshot,
  finalizeTechnicianCorrectionEvidenceUpload,
  listTechnicianCorrectionEvidenceItems,
  listTechnicianCorrectionEvidenceSlots,
  listTechnicianCorrections,
  putTechnicianEvidenceUpload,
  resubmitTechnicianCorrection,
  sha256Hex,
  startTechnicianCorrection,
  type TechnicianCorrection,
  type TechnicianEvidenceItem,
  type TechnicianEvidenceSlot,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'

const props = defineProps<{ technicianContextId: string | null }>()
const route = useRoute()
const correction = ref<TechnicianCorrection | null>(null)
const slots = ref<TechnicianEvidenceSlot[]>([])
const items = ref<TechnicianEvidenceItem[]>([])
const files = ref<Record<string, File | undefined>>({})
const busy = ref(false)
const uploadingSlotId = ref<string | null>(null)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

function itemsForSlot(slotId: string) {
  return items.value.filter((item) => item.evidenceSlotId === slotId)
}

function selectFile(slotId: string, event: Event) {
  files.value[slotId] = (event.target as HTMLInputElement).files?.[0]
}

const validatedRevisionCount = computed(() =>
  items.value
    .flatMap((item) => item.revisions ?? [])
    .filter((revision) => revision.status === 'VALIDATED').length,
)

const correctionSteps = computed(() => {
  const current = correction.value
  if (!current) return []
  const claimed = current.taskStatus === 'CLAIMED' || current.taskStatus === 'RUNNING'
  const running = current.taskStatus === 'RUNNING'
  const hasValidated = validatedRevisionCount.value > 0
  const blocked = Boolean(current.clientCapabilityUnsupportedDetail)
  return [
    {
      key: 'claim',
      label: '领取 / 启动',
      status: blocked ? 'blocked' : running ? 'done' : claimed ? 'current' : 'current',
    },
    {
      key: 'upload',
      label: '补传资料',
      status: blocked
        ? 'blocked'
        : hasValidated
          ? 'done'
          : running
            ? 'current'
            : 'upcoming',
    },
    {
      key: 'resubmit',
      label: '冻结并重提',
      status: blocked
        ? 'blocked'
        : hasValidated
          ? 'current'
          : 'upcoming',
    },
  ]
})

const stickyLabel = computed(() => {
  const current = correction.value
  if (!current) return '加载整改…'
  if (current.clientCapabilityUnsupportedDetail) return '当前客户端无法履约'
  if (current.taskStatus === 'READY') return busy.value ? '领取中…' : '领取整改任务'
  if (current.taskStatus === 'CLAIMED') return busy.value ? '启动中…' : '启动整改任务'
  if (validatedRevisionCount.value === 0) return '请先补传并通过校验的资料'
  return busy.value ? '重提处理中…' : '冻结快照并重提'
})

const stickyDisabled = computed(() => {
  const current = correction.value
  if (!current || busy.value || uploadingSlotId.value) return true
  if (current.clientCapabilityUnsupportedDetail) return true
  if (current.taskStatus === 'READY' || current.taskStatus === 'CLAIMED') return false
  return validatedRevisionCount.value === 0
})

async function onSticky() {
  const current = correction.value
  if (!current || stickyDisabled.value) return
  if (current.taskStatus !== 'RUNNING') {
    await claimOrStart()
    return
  }
  await resubmit()
}

async function loadEvidence() {
  const context = props.technicianContextId
  const current = correction.value
  if (
    !context ||
    !current ||
    current.taskStatus !== 'RUNNING' ||
    current.clientCapabilityUnsupportedDetail
  ) {
    slots.value = []
    items.value = []
    return
  }
  ;[slots.value, items.value] = await Promise.all([
    listTechnicianCorrectionEvidenceSlots(context, current.correctionCaseId),
    listTechnicianCorrectionEvidenceItems(context, current.correctionCaseId),
  ])
}

async function load() {
  if (!props.technicianContextId) {
    correction.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  try {
    const id = String(route.params.id ?? '')
    correction.value =
      (await listTechnicianCorrections(props.technicianContextId)).find(
        (item) => item.correctionCaseId === id,
      ) ?? null
    if (!correction.value) throw new Error('整改任务不存在或当前责任已失效')
    await loadEvidence()
    error.value = null
  } catch (err) {
    correction.value = null
    slots.value = []
    items.value = []
    error.value = userFacingError(err, '整改任务加载失败')
  }
}

async function claimOrStart() {
  const context = props.technicianContextId
  const current = correction.value
  if (!context || !current || busy.value || current.clientCapabilityUnsupportedDetail) return
  busy.value = true
  try {
    const result =
      current.taskStatus === 'READY'
        ? await claimTechnicianCorrection(context, current.correctionCaseId, current.taskVersion)
        : await startTechnicianCorrection(context, current.correctionCaseId, current.taskVersion)
    correction.value = result.data
    message.value =
      result.data.taskStatus === 'RUNNING'
        ? '整改任务已启动，可以补传资料'
        : '整改任务已领取，请继续启动'
    await loadEvidence()
  } catch (err) {
    message.value = userFacingError(err, '整改任务状态更新失败，请刷新后重试')
  } finally {
    busy.value = false
  }
}

async function upload(slot: TechnicianEvidenceSlot) {
  const context = props.technicianContextId
  const current = correction.value
  const file = files.value[slot.slotId]
  if (
    !context ||
    !current ||
    !file ||
    uploadingSlotId.value ||
    current.clientCapabilityUnsupportedDetail
  ) {
    return
  }
  uploadingSlotId.value = slot.slotId
  try {
    message.value = '正在计算文件摘要并创建受限上传会话…'
    const digest = await sha256Hex(file)
    const existing = itemsForSlot(slot.slotId)
    const evidenceItemId =
      slot.maxCount !== null && existing.length >= slot.maxCount
        ? (existing.at(-1)?.evidenceItemId ?? null)
        : null
    const session = (
      await beginTechnicianCorrectionEvidenceUpload(context, current.correctionCaseId, slot.slotId, {
        evidenceItemId,
        originalFileName: file.name || `correction-${crypto.randomUUID()}`,
        declaredMimeType: file.type || 'application/octet-stream',
        expectedSize: file.size,
        expectedSha256: digest,
        captureSource: 'FILE',
        capturedAt: new Date(file.lastModified || Date.now()).toISOString(),
      })
    ).data
    await putTechnicianEvidenceUpload(session, file)
    await finalizeTechnicianCorrectionEvidenceUpload(
      context,
      current.correctionCaseId,
      slot.slotId,
      session.uploadSessionId,
      digest,
    )
    files.value[slot.slotId] = undefined
    message.value = '资料已 Finalize；请等待服务器扫描和机器校验后刷新'
    await loadEvidence()
  } catch (err) {
    message.value = userFacingError(err, '整改资料上传失败，请重新选择文件后重试')
  } finally {
    uploadingSlotId.value = null
  }
}

async function resubmit() {
  const context = props.technicianContextId
  const current = correction.value
  if (!context || !current || busy.value || current.clientCapabilityUnsupportedDetail) return
  const revisionIds = items.value
    .map(
      (item) =>
        item.revisions
          .filter((revision) => revision.status === 'VALIDATED')
          .sort((left, right) => right.revisionNumber - left.revisionNumber)[0]?.evidenceRevisionId,
    )
    .filter((id): id is string => Boolean(id))
  if (revisionIds.length === 0) {
    message.value = '没有可冻结的 VALIDATED 资料版本'
    return
  }
  busy.value = true
  try {
    message.value = '正在冻结新的 TASK_SUBMISSION Snapshot…'
    const snapshot = await createTechnicianCorrectionEvidenceSetSnapshot(
      context,
      current.correctionCaseId,
      revisionIds,
    )
    const result = await resubmitTechnicianCorrection(
      context,
      current.correctionCaseId,
      snapshot.data.evidenceSetSnapshotId,
    )
    correction.value = result.data
    message.value = `整改已第 ${result.data.resubmissionCount} 次重提，任务保持进行中直至审核关闭`
  } catch (err) {
    message.value = userFacingError(err, '整改重提失败，请刷新后重试')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.technicianContextId, route.params.id],
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="technician-correction-detail"
    data-page-id="TECHNICIAN.TASK.DETAIL"
    class="correction-page"
  >
    <header class="top">
      <div>
        <RouterLink to="/technician-portal/task-feed">← 返回今日任务</RouterLink>
        <p class="eyebrow">整改任务</p>
        <h2>整改处理</h2>
        <p class="hint">只处理被驳回项；补传形成新 Revision，不能修改审核结论。</p>
      </div>
      <button type="button" class="ghost" data-testid="technician-correction-refresh" @click="load">
        刷新
      </button>
    </header>

    <p v-if="error" data-testid="technician-correction-error">{{ error }}</p>
    <template v-else-if="correction">
      <ol class="steps" data-testid="technician-correction-steps" aria-label="整改步骤">
        <li
          v-for="step in correctionSteps"
          :key="step.key"
          :data-status="step.status"
          :data-testid="`technician-correction-step-${step.key}`"
        >
          {{ step.label }}
        </li>
      </ol>

      <dl class="summary">
        <div>
          <dt>整改状态</dt>
          <dd>{{ statusLabel(correction.caseStatus) }}</dd>
        </div>
        <div>
          <dt>任务状态</dt>
          <dd data-testid="technician-correction-task-status">
            {{ statusLabel(correction.taskStatus) }}
          </dd>
        </div>
        <div>
          <dt>驳回原因</dt>
          <dd data-testid="technician-correction-reasons">
            {{ correction.reasonCodes.map((code) => statusLabel(code)).join(' / ') }}
          </dd>
        </div>
        <div>
          <dt>历史重提</dt>
          <dd>{{ correction.resubmissionCount }} 次</dd>
        </div>
      </dl>

      <p
        v-if="correction.clientCapabilityUnsupportedDetail"
        class="capability-warn"
        data-testid="technician-correction-capability"
        role="alert"
      >
        {{ correction.clientCapabilityUnsupportedDetail }}
      </p>

      <button
        v-if="correction.taskStatus !== 'RUNNING' && !correction.clientCapabilityUnsupportedDetail"
        type="button"
        class="primary"
        :disabled="busy"
        data-testid="technician-correction-lifecycle"
        @click="claimOrStart"
      >
        {{ correction.taskStatus === 'READY' ? '领取整改任务' : '启动整改任务' }}
      </button>

      <section
        v-else-if="correction.taskStatus === 'RUNNING' && !correction.clientCapabilityUnsupportedDetail"
        data-testid="technician-correction-evidence"
      >
        <h3>补传资料</h3>
        <p class="hint">源业务任务保持完成状态；本页仅通过整改任务追加新 Revision。</p>
        <p v-if="slots.length === 0">暂无资料槽位</p>
        <article
          v-for="slot in slots"
          :key="slot.slotId"
          class="slot"
          :data-testid="`technician-correction-slot-${slot.slotId}`"
        >
          <div>
            <strong>{{ slot.requirementName }}</strong>
            <span>{{ statusLabel(slot.status) }} · {{ itemsForSlot(slot.slotId).length }} 项</span>
          </div>
          <ul>
            <li v-for="item in itemsForSlot(slot.slotId)" :key="item.evidenceItemId">
              Item {{ item.itemOrdinal }} ·
              {{ statusLabel(item.revisions.at(-1)?.status) }} / Revision
              {{ item.revisions.at(-1)?.revisionNumber }}
            </li>
          </ul>
          <input
            type="file"
            :data-testid="`technician-correction-file-${slot.slotId}`"
            @change="selectFile(slot.slotId, $event)"
          />
          <button
            type="button"
            :disabled="!files[slot.slotId] || uploadingSlotId !== null"
            :data-testid="`technician-correction-upload-${slot.slotId}`"
            @click="upload(slot)"
          >
            上传并 Finalize
          </button>
        </article>
        <button
          type="button"
          class="primary"
          :disabled="busy || uploadingSlotId !== null || validatedRevisionCount === 0"
          data-testid="technician-correction-resubmit"
          @click="resubmit"
        >
          冻结快照并重提
        </button>
      </section>

      <p v-if="message" role="status" data-testid="technician-correction-message">{{ message }}</p>

      <footer class="sticky-action" data-testid="technician-correction-sticky">
        <button
          type="button"
          class="primary"
          :disabled="stickyDisabled"
          data-testid="technician-correction-sticky-button"
          @click="onSticky"
        >
          {{ stickyLabel }}
        </button>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.correction-page {
  display: grid;
  gap: 12px;
  padding-bottom: 96px;
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.eyebrow {
  margin: 0.35rem 0 0.2rem;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.top h2 {
  margin: 0 0 4px;
  font-size: 22px;
}
.hint {
  color: var(--sos-color-text-tertiary);
  font-size: 0.9rem;
}
.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.steps li {
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  background: #fff;
}
.steps li[data-status='done'] {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #389e0d;
}
.steps li[data-status='current'] {
  border-color: var(--sos-primary-600, #1677ff);
  background: var(--sos-primary-100, #e6f4ff);
  color: var(--sos-primary-800, #003eb3);
  font-weight: 600;
}
.steps li[data-status='blocked'] {
  border-color: #ffccc7;
  background: #fff2f0;
  color: #cf1322;
}
.summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.7rem;
}
.summary div,
.slot {
  padding: 0.8rem;
  border: 1px solid #e4e7ec;
  border-radius: 0.7rem;
  background: #fff;
}
.summary dt {
  color: #667085;
  font-size: 0.85rem;
}
.summary dd {
  margin: 0.25rem 0 0;
}
.slot {
  margin: 0.75rem 0;
  display: grid;
  gap: 8px;
}
.slot > div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
}
.slot span {
  color: #667085;
}
.capability-warn {
  margin: 0;
  padding: 0.75rem 0.9rem;
  border: 1px solid #fdba74;
  border-radius: 0.7rem;
  background: #fff7ed;
  color: #9a3412;
}
button.ghost,
button.primary {
  min-height: 44px;
  border-radius: 10px;
  padding: 0 12px;
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  background: #fff;
}
button.primary {
  background: var(--sos-primary-600, #1677ff);
  border-color: var(--sos-primary-600, #1677ff);
  color: #fff;
  font-weight: 700;
}
button:disabled {
  opacity: 0.55;
}
.sticky-action {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(64px + env(safe-area-inset-bottom, 0px));
  z-index: 25;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--sos-color-border-default, #e5e7eb);
}
.sticky-action .primary {
  width: 100%;
  min-height: 48px;
}
@media (min-width: 900px) {
  .sticky-action {
    bottom: 0;
    left: calc(260px + 48px);
    right: 24px;
  }
}
</style>
