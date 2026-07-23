<script setup lang="ts">
import type {
  WorkOrderWorkspaceCorrectionCaseSummary,
  WorkOrderWorkspaceReviewCaseSummary,
  WorkOrderWorkspaceReviewsCorrectionsSectionData,
} from '@serviceos/api-client'
import {
  createReviewCase,
  decideReviewCase,
  getEvidenceSetSnapshot,
  getReviewCase,
} from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { Button, Checkbox, Empty, Input } from '@serviceos/design-system'
import { message, Modal } from 'ant-design-vue'
import { computed, ref } from 'vue'
import {
  correctionStatusLabel,
  formatDateTime,
  reviewDecisionLabel,
  reviewOriginLabel,
  reviewReasonCodeText,
  reviewStatusLabel,
} from '../presenters/work-order'
import PageError from './PageError.vue'
import StatusPill from './StatusPill.vue'

/**
 * 工单工作区「审核整改」区块。
 * 裁决按契约走三步：读取 ReviewCase 取 aggregateVersion（If-Match），
 * 读取冻结 Snapshot 取 EvidenceRevision 目标，再提交 targetDecisions；
 * 整组结论由服务端派生，CLIENT 审核单只读、不提供裁决入口。
 */
const props = defineProps<{ data: WorkOrderWorkspaceReviewsCorrectionsSectionData | null }>()

const queryClient = useQueryClient()

const reviews = computed(() => props.data?.reviews ?? [])
const corrections = computed(() => props.data?.corrections ?? [])
const isEmpty = computed(() => !reviews.value.length && !corrections.value.length)

const rejectReasonOptions = [
  { value: 'PHOTO_BLURRY', label: '照片模糊' },
  { value: 'PHOTO_MISSING', label: '缺少必传照片' },
  { value: 'INFO_MISMATCH', label: '信息不一致' },
  { value: 'OTHER', label: '其他' },
]

type DecideTarget = { review: WorkOrderWorkspaceReviewCaseSummary; mode: 'APPROVED' | 'REJECTED' }
const decideTarget = ref<DecideTarget | null>(null)
const rejectReasons = ref<string[]>([])
const decideNote = ref('')

function errorDetail(error: unknown): string {
  return error instanceof Error ? error.message : '操作未能完成，请稍后重试'
}

async function refreshWorkspace() {
  await queryClient.invalidateQueries({ queryKey: ['work-order-workspace'] })
}

const decideCommand = useMutation({
  mutationFn: async (target: DecideTarget) => {
    if (target.mode === 'REJECTED' && !rejectReasons.value.length) {
      throw new Error('审核驳回至少需要一个原因码')
    }
    const current = await getReviewCase(target.review.reviewCaseId)
    if (current.status !== 'OPEN') throw new Error('该审核单已被裁决，请刷新后查看最新状态')
    const snapshot = await getEvidenceSetSnapshot(target.review.evidenceSetSnapshotId)
    if (!snapshot.members.length) throw new Error('审核快照中没有可裁决的资料版本')
    const note = decideNote.value.trim() || null
    return decideReviewCase(target.review.reviewCaseId, current.aggregateVersion, {
      targetDecisions: snapshot.members.map((member) => ({
        targetType: 'EvidenceRevision' as const,
        targetId: member.evidenceRevisionId,
        targetVersion: member.revisionNumber,
        decision: target.mode,
        ...(target.mode === 'REJECTED' ? { reasonCodes: [...rejectReasons.value] } : {}),
      })),
      note,
    })
  },
  onSuccess: async (_result, target) => {
    message.success(target.mode === 'APPROVED' ? '审核已通过' : '审核已驳回，已生成整改要求')
    decideTarget.value = null
    await refreshWorkspace()
  },
})

function openDecide(review: WorkOrderWorkspaceReviewCaseSummary, mode: 'APPROVED' | 'REJECTED') {
  decideCommand.reset()
  rejectReasons.value = []
  decideNote.value = ''
  decideTarget.value = { review, mode }
}

const resubmitReviewCommand = useMutation({
  mutationFn: async (correction: WorkOrderWorkspaceCorrectionCaseSummary) => {
    if (!correction.latestResubmissionSnapshotId) {
      throw new Error('整改单缺少最新重提快照，无法发起复审')
    }
    return createReviewCase({ evidenceSetSnapshotId: correction.latestResubmissionSnapshotId })
  },
  onSuccess: async () => {
    message.success('已发起复审，生成新的平台审核单')
    await refreshWorkspace()
  },
  onError: (error) => message.error(errorDetail(error)),
})

function reviewStatusTone(status: WorkOrderWorkspaceReviewCaseSummary['status']) {
  if (status === 'APPROVED' || status === 'FORCE_APPROVED') return 'green'
  if (status === 'REJECTED') return 'red'
  if (status === 'OPEN') return 'blue'
  return 'gray'
}

function correctionStatusTone(status: WorkOrderWorkspaceCorrectionCaseSummary['status']) {
  if (status === 'CLOSED' || status === 'WAIVED') return 'green'
  if (status === 'RESUBMITTED') return 'blue'
  return 'orange'
}

function canDecide(review: WorkOrderWorkspaceReviewCaseSummary): boolean {
  return review.status === 'OPEN' && review.origin === 'INTERNAL'
}
</script>

<template>
  <div class="reviews-corrections-section">
    <Empty
      v-if="isEmpty"
      description="当前工单没有审核单与整改单"
    />
    <template v-else>
      <section class="rc-block">
        <h3>审核单</h3>
        <Empty
          v-if="!reviews.length"
          description="暂无审核单"
        />
        <article
          v-for="review in reviews"
          :key="review.reviewCaseId"
          class="rc-card"
        >
          <header>
            <span class="rc-title">
              <StatusPill
                :tone="reviewStatusTone(review.status)"
                :label="reviewStatusLabel(review.status)"
              />
              <em
                class="rc-origin"
                :class="{ client: review.origin === 'CLIENT' }"
              >{{ reviewOriginLabel(review.origin) }}</em>
              <small>策略版本 {{ review.policyVersion }}</small>
            </span>
            <span
              v-if="canDecide(review)"
              class="rc-actions"
            >
              <Button
                type="primary"
                size="small"
                @click="openDecide(review, 'APPROVED')"
              >审核通过</Button>
              <Button
                danger
                size="small"
                @click="openDecide(review, 'REJECTED')"
              >审核驳回</Button>
            </span>
          </header>
          <dl class="rc-facts">
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(review.createdAt) }}</dd>
            </div>
            <div>
              <dt>裁决时间</dt>
              <dd>{{ formatDateTime(review.decidedAt) }}</dd>
            </div>
            <div>
              <dt>资料快照</dt>
              <dd class="rc-mono">
                {{ review.evidenceSetSnapshotId.slice(0, 8) }}…
              </dd>
            </div>
          </dl>
          <ol
            v-if="review.decisions.length"
            class="rc-decisions"
          >
            <li
              v-for="decision in review.decisions"
              :key="decision.reviewDecisionId"
            >
              <strong>第 {{ decision.decisionOrdinal }} 轮 · {{ reviewDecisionLabel(decision.decision) }}</strong>
              <span v-if="decision.reasonCodes.length">原因：{{ reviewReasonCodeText(decision.reasonCodes) }}</span>
              <small>{{ formatDateTime(decision.decidedAt) }}</small>
            </li>
          </ol>
          <p
            v-if="review.origin === 'CLIENT'"
            class="rc-readonly-hint"
          >
            车企审核单由车企侧裁决，平台只读展示。
          </p>
        </article>
      </section>

      <section class="rc-block">
        <h3>整改单</h3>
        <Empty
          v-if="!corrections.length"
          description="暂无整改单"
        />
        <article
          v-for="correction in corrections"
          :key="correction.correctionCaseId"
          class="rc-card"
        >
          <header>
            <span class="rc-title">
              <StatusPill
                :tone="correctionStatusTone(correction.status)"
                :label="correctionStatusLabel(correction.status)"
              />
              <small>重提 {{ correction.resubmissions.length }} 次</small>
            </span>
            <Button
              v-if="correction.status === 'RESUBMITTED'"
              type="primary"
              size="small"
              :loading="
                resubmitReviewCommand.isPending.value &&
                  resubmitReviewCommand.variables.value?.correctionCaseId === correction.correctionCaseId
              "
              @click="resubmitReviewCommand.mutate(correction)"
            >
              发起复审
            </Button>
          </header>
          <dl class="rc-facts">
            <div>
              <dt>整改原因</dt>
              <dd>{{ reviewReasonCodeText(correction.reasonCodes) || '—' }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(correction.createdAt) }}</dd>
            </div>
            <div>
              <dt>最新重提快照</dt>
              <dd class="rc-mono">
                {{ correction.latestResubmissionSnapshotId ? `${correction.latestResubmissionSnapshotId.slice(0, 8)}…` : '—' }}
              </dd>
            </div>
          </dl>
          <ol
            v-if="correction.resubmissions.length"
            class="rc-decisions"
          >
            <li
              v-for="resubmission in correction.resubmissions"
              :key="resubmission.correctionResubmissionId"
            >
              <strong>第 {{ resubmission.resubmissionOrdinal }} 次重提</strong>
              <small>{{ formatDateTime(resubmission.submittedAt) }}</small>
            </li>
          </ol>
        </article>
      </section>
    </template>

    <Modal
      :open="decideTarget !== null"
      :title="decideTarget?.mode === 'APPROVED' ? '审核通过' : '审核驳回'"
      :ok-text="decideTarget?.mode === 'APPROVED' ? '确认通过' : '确认驳回'"
      cancel-text="取消"
      :confirm-loading="decideCommand.isPending.value"
      @ok="decideTarget && decideCommand.mutate(decideTarget)"
      @cancel="decideTarget = null"
    >
      <p class="rc-modal-intro">
        {{ decideTarget?.mode === 'APPROVED'
          ? '将对快照内全部资料版本裁决为通过，工单进入后续回传流程。'
          : '将对快照内全部资料版本裁决为驳回，并同事务生成整改单。' }}
      </p>
      <template v-if="decideTarget?.mode === 'REJECTED'">
        <p class="rc-field-label">
          驳回原因码（至少选择一项）
        </p>
        <Checkbox.Group
          v-model:value="rejectReasons"
          class="rc-reason-group"
          :options="rejectReasonOptions"
        />
      </template>
      <p class="rc-field-label">
        备注（可选）
      </p>
      <Input.TextArea
        v-model:value="decideNote"
        :rows="3"
        :maxlength="1000"
        placeholder="补充说明本次裁决依据"
      />
      <PageError
        v-if="decideCommand.isError.value"
        :detail="errorDetail(decideCommand.error.value)"
      />
    </Modal>
  </div>
</template>

<style scoped>
.reviews-corrections-section {
  display: grid;
  gap: 16px;
}

.rc-block {
  display: grid;
  gap: 10px;
  align-content: start;
}

.rc-block > h3 {
  margin: 0 0 2px;
  color: var(--sos-text-strong);
  font-size: 14px;
}

.rc-card {
  display: grid;
  gap: 10px;
  padding: 13px 15px;
  border: 1px solid var(--sos-border-soft);
  border-radius: 7px;
}

.rc-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rc-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.rc-title small {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.rc-origin {
  color: var(--sos-brand);
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
}

.rc-origin.client {
  color: var(--sos-orange);
}

.rc-actions {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 8px;
}

.rc-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 18px;
  margin: 0;
}

.rc-facts dt {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.rc-facts dd {
  margin: 3px 0 0;
  color: var(--sos-text-strong);
  font-size: 12px;
}

.rc-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.rc-decisions {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 8px 0 0;
  border-top: 1px dashed var(--sos-border-soft);
  list-style: none;
}

.rc-decisions li {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.rc-decisions strong {
  color: var(--sos-text-strong);
  font-size: 12px;
}

.rc-decisions span {
  color: var(--sos-text-secondary);
  font-size: 12px;
}

.rc-decisions small {
  margin-left: auto;
  color: var(--sos-text-muted);
  font-size: 11px;
}

.rc-readonly-hint {
  margin: 0;
  padding-top: 8px;
  border-top: 1px dashed var(--sos-border-soft);
  color: var(--sos-text-muted);
  font-size: 11px;
}

.rc-modal-intro {
  margin: 0 0 12px;
  color: var(--sos-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.rc-field-label {
  margin: 12px 0 7px;
  color: var(--sos-text-strong);
  font-size: 13px;
  font-weight: 600;
}

.rc-reason-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
</style>
