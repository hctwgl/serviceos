<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Empty,
  Image,
  Input,
  Modal,
  Radio,
  Space,
  Tag,
  Typography,
} from 'ant-design-vue'
import AsyncContent from '../../../../components/feedback/AsyncContent.vue'
import PermissionBoundary from '../../../../components/feedback/PermissionBoundary.vue'
import AllowedActionButton from '../../../../components/business/AllowedActionButton.vue'
import SlaCountdown from '../../../../components/business/SlaCountdown.vue'
import WorkOrderStatusTag from '../../../../components/business/WorkOrderStatusTag.vue'
import SensitiveText from '../../../../components/business/SensitiveText.vue'
import {
  authorizeEvidenceRevisionDownload,
  getFinalReviewWorkspaceSection,
  type FinalReviewTarget,
  type FinalReviewWorkspaceSection,
} from '../../../../api/finalReview'
import { decideReviewCase } from '../../../../api/reviews'
import {
  clearFinalReviewDraftsForPrincipal,
  loadFinalReviewDraft,
  saveFinalReviewDraft,
  type LocalTargetDecision,
} from '../../composables/useFinalReviewDraft'
import { AUTH_REQUIRED_EVENT } from '../../../../auth/oidc'

const props = defineProps<{ workOrderId: string }>()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref<string | null>(null)
const section = ref<FinalReviewWorkspaceSection | null>(null)
const filter = ref<'ALL' | 'PENDING' | 'REJECTED'>('ALL')
const selectedTargetId = ref<string | null>(null)
const decisions = ref<Record<string, LocalTargetDecision>>({})
const overallNote = ref('')
const draftStatus = ref('尚未暂存')
const previewUrl = ref<string | null>(null)
const previewError = ref<string | null>(null)
const staleDraft = ref(false)

const data = computed(() => section.value?.data ?? null)
const targets = computed(() =>
  (data.value?.targetGroups ?? []).flatMap((group) => group.targets),
)
const selected = computed(() =>
  targets.value.find((target) => target.targetId === selectedTargetId.value) ?? null,
)
const decideAction = computed(() =>
  data.value?.allowedActions.find((action) => action.action === 'DECIDE') ?? null,
)
const readonlyMode = computed(() => !decideAction.value?.enabled)
const decidedCount = computed(
  () => Object.values(decisions.value).filter((item) => item.decision).length,
)
const rejectedCount = computed(
  () => Object.values(decisions.value).filter((item) => item.decision === 'REJECTED').length,
)
const filteredTargets = computed(() => {
  if (filter.value === 'PENDING') {
    return targets.value.filter((target) => !decisions.value[target.targetId]?.decision)
  }
  if (filter.value === 'REJECTED') {
    return targets.value.filter((target) => decisions.value[target.targetId]?.decision === 'REJECTED')
  }
  return targets.value
})

const primaryLabel = computed(() => {
  if (decidedCount.value < targets.value.length) return '提交终审'
  if (rejectedCount.value > 0) return '驳回整改'
  return '审核通过'
})
const submitEnabled = computed(() => {
  if (readonlyMode.value) return false
  if (!data.value?.reviewCase) return false
  if (targets.value.length === 0) return false
  if (decidedCount.value < targets.value.length) return false
  for (const target of targets.value) {
    const decision = decisions.value[target.targetId]
    if (!decision?.decision) return false
    if (decision.decision === 'REJECTED') {
      if (!decision.reasonCodes.length || !decision.note.trim()) return false
    }
  }
  return !!decideAction.value?.enabled
})
const submitBusy = ref(false)
const conflictVisible = ref(false)
const conflictMessage = ref('')
const copiedOpinions = ref('')
const submitBlockReason = computed(() => {
  if (readonlyMode.value) return decideAction.value?.reason || '当前不可提交终审'
  if (decidedCount.value < targets.value.length) return '仍有未处理的审核目标'
  if (!submitEnabled.value) return '请补全驳回原因与说明'
  return null
})
const openCorrectionId = computed(() => data.value?.openCorrectionCaseId ?? null)

async function load() {
  loading.value = true
  error.value = null
  try {
    section.value = await getFinalReviewWorkspaceSection(props.workOrderId)
    const reviewCase = section.value.data.reviewCase
    const defaultId =
      (typeof route.query.targetId === 'string' && route.query.targetId)
      || section.value.data.defaultTargetRef?.targetId
      || section.value.data.targetGroups[0]?.targets[0]?.targetId
      || null
    selectedTargetId.value = defaultId
    decisions.value = {}
    overallNote.value = ''
    staleDraft.value = false
    if (reviewCase) {
      const draft = loadFinalReviewDraft(reviewCase.reviewCaseId, reviewCase.aggregateVersion)
      if (draft) {
        if (draft.aggregateVersion !== reviewCase.aggregateVersion) {
          staleDraft.value = true
        } else {
          overallNote.value = draft.overallNote
          for (const item of draft.targetDecisions) {
            decisions.value[item.targetId] = item
          }
          draftStatus.value = '已暂存到当前浏览器'
        }
      }
      ensureDecision(selected.value)
    }
    await loadPreview()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载终审工作区失败'
    section.value = null
  } finally {
    loading.value = false
  }
}

function ensureDecision(target: FinalReviewTarget | null) {
  if (!target) return
  if (!decisions.value[target.targetId]) {
    decisions.value[target.targetId] = {
      targetType: 'EvidenceRevision',
      targetId: target.targetId,
      targetVersion: target.targetVersion,
      decision: null,
      reasonCodes: [],
      note: '',
    }
  }
}

function persistDraft() {
  const reviewCase = data.value?.reviewCase
  if (!reviewCase) return
  saveFinalReviewDraft({
    reviewCaseId: reviewCase.reviewCaseId,
    aggregateVersion: reviewCase.aggregateVersion,
    overallNote: overallNote.value,
    targetDecisions: Object.values(decisions.value),
  })
  draftStatus.value = '已暂存到当前浏览器'
}

async function loadPreview() {
  previewUrl.value = null
  previewError.value = null
  const target = selected.value
  if (!target) return
  try {
    const auth = await authorizeEvidenceRevisionDownload(target.revisionId, 'FINAL_REVIEW_PREVIEW')
    previewUrl.value = auth.downloadUrl
  } catch (err) {
    previewError.value = err instanceof Error ? err.message : '预览授权失败'
  }
}

function selectTarget(targetId: string) {
  selectedTargetId.value = targetId
  void router.replace({ query: { ...route.query, tab: 'FINAL_REVIEW', targetId } })
  ensureDecision(targets.value.find((item) => item.targetId === targetId) ?? null)
  persistDraft()
  void loadPreview()
}

function setDecision(decision: 'APPROVED' | 'REJECTED') {
  const target = selected.value
  if (!target || readonlyMode.value) return
  ensureDecision(target)
  decisions.value[target.targetId].decision = decision
  if (decision === 'APPROVED') {
    decisions.value[target.targetId].reasonCodes = []
    decisions.value[target.targetId].note = ''
  }
  persistDraft()
}

function toggleReason(code: string, checked: boolean) {
  const target = selected.value
  if (!target) return
  ensureDecision(target)
  const current = new Set(decisions.value[target.targetId].reasonCodes)
  if (checked) current.add(code)
  else current.delete(code)
  decisions.value[target.targetId].reasonCodes = [...current]
  persistDraft()
}

function onLogoutClear() {
  clearFinalReviewDraftsForPrincipal()
}

function copyUnsavedOpinions() {
  const lines = targets.value.map((target) => {
    const local = decisions.value[target.targetId]
    if (!local?.decision) return `${target.requirementLabel}: 未处理`
    if (local.decision === 'APPROVED') return `${target.requirementLabel}: 通过`
    return `${target.requirementLabel}: 驳回 (${local.reasonCodes.join(',')}) ${local.note}`
  })
  if (overallNote.value) lines.push(`整单说明: ${overallNote.value}`)
  copiedOpinions.value = lines.join('\n')
  void navigator.clipboard?.writeText(copiedOpinions.value)
}

async function submitDecide() {
  const reviewCase = data.value?.reviewCase
  if (!reviewCase || !submitEnabled.value) return
  const approved = decidedCount.value - rejectedCount.value
  Modal.confirm({
    title: primaryLabel.value,
    content: `审核目标 ${targets.value.length} 项：通过 ${approved}，驳回 ${rejectedCount.value}，未处理 ${targets.value.length - decidedCount.value}。整组结果由服务端派生，不会伪造预结算。`,
    okText: '确认提交',
    cancelText: '取消',
    async onOk() {
      submitBusy.value = true
      try {
        const body = {
          targetDecisions: targets.value.map((target) => {
            const local = decisions.value[target.targetId]
            return {
              targetType: 'EvidenceRevision' as const,
              targetId: target.targetId,
              targetVersion: target.targetVersion,
              decision: local.decision as 'APPROVED' | 'REJECTED',
              reasonCodes: local.decision === 'REJECTED' ? local.reasonCodes : [],
              note: local.decision === 'REJECTED' ? local.note : null,
            }
          }),
          note: overallNote.value || null,
        }
        const result = await decideReviewCase(
          reviewCase.reviewCaseId,
          body,
          `"${reviewCase.aggregateVersion}"`,
        )
        draftStatus.value = '已暂存到当前浏览器'
        const decided = result.data
        if (decided.status === 'REJECTED' && decided.correctionCaseId) {
          await router.push({
            name: 'ADMIN.CORRECTION.DETAIL',
            params: { id: decided.correctionCaseId },
          })
          return
        }
        await load()
      } catch (err) {
        const status = (err as { status?: number }).status
        if (status === 409) {
          persistDraft()
          conflictVisible.value = true
          conflictMessage.value =
            '审核案例版本冲突或已被他人处理。本地草稿已保留，不会自动覆盖服务端最新数据。'
          await load()
          return
        }
        error.value = err instanceof Error ? err.message : '提交终审失败'
      } finally {
        submitBusy.value = false
      }
    },
  })
}

onMounted(() => {
  void load()
  window.addEventListener(AUTH_REQUIRED_EVENT, onLogoutClear)
})
onUnmounted(() => {
  window.removeEventListener(AUTH_REQUIRED_EVENT, onLogoutClear)
})

watch(
  () => props.workOrderId,
  () => {
    void load()
  },
)
</script>

<template>
  <div class="final-review-workspace" data-testid="final-review-workspace">
    <AsyncContent :loading="loading" :error="error" :empty="!data">
      <PermissionBoundary :readonly-mode="readonlyMode" :reason="decideAction?.reason">
        <Card size="small" class="summary-card">
          <Descriptions :column="4" size="small">
            <Descriptions.Item label="工单编号">
              {{ data?.workOrder.displayNo }}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <WorkOrderStatusTag :status="data?.workOrder.statusCode || ''" />
            </Descriptions.Item>
            <Descriptions.Item label="项目">
              <SensitiveText :value="data?.workOrder.projectName || data?.workOrder.projectId" />
            </Descriptions.Item>
            <Descriptions.Item label="下一步">
              {{ data?.workOrder.nextActionLabel }}
            </Descriptions.Item>
            <Descriptions.Item label="客户">
              <SensitiveText :value="data?.workOrder.maskedCustomerName" />
            </Descriptions.Item>
            <Descriptions.Item label="电话">
              <SensitiveText :value="data?.workOrder.maskedCustomerPhone" />
            </Descriptions.Item>
            <Descriptions.Item label="地址" :span="2">
              <SensitiveText :value="data?.workOrder.maskedServiceAddress" />
            </Descriptions.Item>
            <Descriptions.Item label="网点">
              <SensitiveText :value="data?.workOrder.networkName" />
            </Descriptions.Item>
            <Descriptions.Item label="师傅">
              <SensitiveText :value="data?.workOrder.technicianName" />
            </Descriptions.Item>
            <Descriptions.Item label="责任人">
              <SensitiveText :value="data?.reviewTask?.assigneeDisplayName" />
            </Descriptions.Item>
            <Descriptions.Item label="SLA">
              <SlaCountdown
                :status="data?.sla?.status"
                :display-text="data?.sla?.displayText"
                :due-at="data?.sla?.dueAt"
              />
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Alert
          v-if="staleDraft"
          type="warning"
          show-icon
          style="margin: 12px 0"
          message="审核案例内容已经更新，旧草稿不会自动覆盖最新数据。"
        />
        <Alert
          v-if="openCorrectionId"
          type="info"
          show-icon
          style="margin: 12px 0"
          message="已关联整改案例"
        >
          <template #action>
            <Button
              type="link"
              data-testid="open-correction-link"
              @click="$router.push({ name: 'ADMIN.CORRECTION.DETAIL', params: { id: openCorrectionId } })"
            >
              打开整改详情
            </Button>
          </template>
        </Alert>
        <Modal
          v-model:open="conflictVisible"
          title="版本冲突"
          ok-text="复制未保存意见"
          cancel-text="关闭"
          data-testid="review-conflict-dialog"
          @ok="copyUnsavedOpinions"
        >
          <p>{{ conflictMessage }}</p>
          <p v-if="copiedOpinions" class="draft-status">意见已复制到剪贴板。</p>
        </Modal>

        <div class="tri-pane">
          <aside class="pane left" data-testid="review-target-list">
            <Typography.Title :level="5">系统门禁</Typography.Title>
            <div class="gate-list">
              <div
                v-for="gate in data?.gateChecks || []"
                :key="gate.code"
                class="gate-item"
                :data-status="gate.status"
              >
                <Tag
                  :color="gate.status === 'PASS' ? 'success' : gate.status === 'FAIL' ? 'error' : gate.status === 'WARN' ? 'warning' : 'default'"
                >
                  {{ gate.label }}
                </Tag>
                <span class="gate-detail">{{ gate.detail || gate.status }}</span>
              </div>
            </div>
            <Typography.Title :level="5">完成度 {{ decidedCount }}/{{ targets.length }}</Typography.Title>
            <Space>
              <Button size="small" :type="filter === 'ALL' ? 'primary' : 'default'" @click="filter = 'ALL'">全部</Button>
              <Button size="small" :type="filter === 'PENDING' ? 'primary' : 'default'" @click="filter = 'PENDING'">待处理</Button>
              <Button size="small" :type="filter === 'REJECTED' ? 'primary' : 'default'" @click="filter = 'REJECTED'">已驳回</Button>
            </Space>
            <div class="target-scroll">
              <button
                v-for="target in filteredTargets"
                :key="target.targetId"
                type="button"
                class="target-item"
                :class="{ active: target.targetId === selectedTargetId }"
                @click="selectTarget(target.targetId)"
              >
                <strong>{{ target.requirementLabel }}</strong>
                <span>{{ target.groupLabel }} · 第 {{ target.revisionNo }} 版</span>
                <Tag v-if="decisions[target.targetId]?.decision === 'APPROVED'" color="success">已通过</Tag>
                <Tag v-else-if="decisions[target.targetId]?.decision === 'REJECTED'" color="error">已驳回</Tag>
                <Tag v-else>待处理</Tag>
              </button>
              <Empty v-if="!filteredTargets.length" description="当前筛选无目标" />
            </div>
          </aside>

          <main class="pane center" data-testid="review-evidence-viewer">
            <template v-if="selected">
              <Typography.Title :level="4">{{ selected.requirementLabel }}</Typography.Title>
              <p class="meta-line">
                资料要求：{{ selected.requirementDescription || '按模板要求采集' }}
              </p>
              <Space wrap>
                <Tag color="cyan">系统预检：{{ selected.validationReadiness }}</Tag>
                <Tag v-if="selected.validationResult" :color="selected.validationResult === 'PASS' ? 'success' : selected.validationResult === 'WARN' ? 'warning' : 'error'">
                  {{ selected.validationResult }}
                </Tag>
                <Tag>{{ selected.lifecycleStatus === 'VALIDATED' ? '已校验' : selected.lifecycleStatus === 'QUARANTINED' ? '安全隔离' : selected.lifecycleStatus }}</Tag>
              </Space>
              <div class="preview-box">
                <Alert v-if="previewError" type="error" :message="previewError" show-icon />
                <Image
                  v-else-if="previewUrl && selected.mimeType?.startsWith('image/')"
                  :src="previewUrl"
                  :alt="selected.requirementLabel"
                  style="max-height: 420px"
                />
                <a v-else-if="previewUrl" :href="previewUrl" target="_blank" rel="noreferrer">打开受控预览</a>
                <Empty v-else description="暂无预览" />
              </div>
              <Descriptions bordered size="small" :column="2" style="margin-top: 12px">
                <Descriptions.Item label="采集时间">{{ selected.capturedAt || '—' }}</Descriptions.Item>
                <Descriptions.Item label="上传人">{{ selected.uploaderDisplayName || '—' }}</Descriptions.Item>
                <Descriptions.Item label="资料版本">第 {{ selected.revisionNo }} 版</Descriptions.Item>
                <Descriptions.Item label="采集来源">{{ selected.captureSource || '—' }}</Descriptions.Item>
                <Descriptions.Item label="脱敏定位">{{ selected.locationVerdict || '—' }}</Descriptions.Item>
                <Descriptions.Item label="离线采集">{{ selected.offline == null ? '—' : selected.offline ? '是' : '否' }}</Descriptions.Item>
                <Descriptions.Item label="机器校验" :span="2">
                  <div v-if="selected.validationMessages.length">
                    <div v-for="msg in selected.validationMessages" :key="msg">{{ msg }}</div>
                  </div>
                  <span v-else>无</span>
                </Descriptions.Item>
              </Descriptions>
            </template>
            <Empty v-else description="请选择左侧审核目标" />
          </main>

          <aside class="pane right" data-testid="review-decision-panel">
            <Typography.Title :level="5">当前目标</Typography.Title>
            <p v-if="selected">
              {{ selected.requirementLabel }}
              <Tag v-if="selected.required" color="red">必审</Tag>
            </p>
            <Radio.Group
              :value="selected ? decisions[selected.targetId]?.decision : null"
              :disabled="readonlyMode || !selected"
              style="display: flex; flex-direction: column; gap: 8px"
              @update:value="(value) => setDecision(value as 'APPROVED' | 'REJECTED')"
            >
              <Radio value="APPROVED">通过</Radio>
              <Radio value="REJECTED">驳回</Radio>
            </Radio.Group>

            <template v-if="selected && decisions[selected.targetId]?.decision === 'REJECTED'">
              <Typography.Title :level="5" style="margin-top: 16px">驳回原因</Typography.Title>
              <div class="reason-list">
                <Checkbox
                  v-for="reason in data?.rejectionReasons || []"
                  :key="reason.code"
                  :checked="decisions[selected.targetId]?.reasonCodes.includes(reason.code)"
                  :disabled="readonlyMode"
                  @update:checked="(checked) => toggleReason(reason.code, !!checked)"
                >
                  {{ reason.label }}
                </Checkbox>
              </div>
              <Typography.Title :level="5" style="margin-top: 16px">审核说明</Typography.Title>
              <Input.TextArea
                :value="decisions[selected.targetId]?.note"
                :disabled="readonlyMode"
                :rows="4"
                placeholder="请填写整改要求"
                @update:value="(value) => {
                  if (!selected) return
                  ensureDecision(selected)
                  decisions[selected.targetId].note = String(value || '')
                  persistDraft()
                }"
              />
            </template>

            <Typography.Title :level="5" style="margin-top: 16px">整单说明</Typography.Title>
            <Input.TextArea
              v-model:value="overallNote"
              :disabled="readonlyMode"
              :rows="3"
              placeholder="可选整单审核说明"
              @change="persistDraft"
            />
            <p class="draft-status" data-testid="draft-status">{{ draftStatus }}</p>

            <div class="blocker-list" data-testid="review-blocker-list">
              <Alert
                v-for="gate in (data?.gateChecks || []).filter((item) => item.blocking && item.status === 'FAIL')"
                :key="gate.code"
                type="error"
                show-icon
                :message="gate.label"
                :description="gate.detail || undefined"
                style="margin-bottom: 8px"
              />
            </div>

            <div class="primary-action" data-testid="review-primary-action">
              <AllowedActionButton
                :label="primaryLabel"
                :enabled="submitEnabled"
                :reason="submitBlockReason"
                :loading="submitBusy"
                primary
                @click="submitDecide"
              />
              <Button style="margin-top: 8px" @click="$router.push(`/work-orders/${workOrderId}`)">
                返回工单
              </Button>
            </div>
          </aside>
        </div>
      </PermissionBoundary>
    </AsyncContent>
  </div>
</template>

<style scoped>
.final-review-workspace {
  min-height: 560px;
}
.summary-card {
  margin-bottom: 16px;
}
.tri-pane {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  gap: 16px;
  min-height: 560px;
  height: calc(100vh - 280px);
}
.pane {
  background: var(--sos-bg-card);
  border: 1px solid var(--sos-border-default);
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}
.gate-list,
.target-scroll,
.reason-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.gate-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.gate-detail {
  color: var(--sos-text-tertiary);
  font-size: 12px;
}
.target-item {
  text-align: left;
  border: 1px solid var(--sos-border-light);
  background: var(--sos-bg-muted);
  border-radius: 6px;
  padding: 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.target-item.active {
  border-color: var(--sos-primary-500);
  background: var(--sos-primary-050);
}
.preview-box {
  margin-top: 12px;
  min-height: 240px;
  background: var(--sos-bg-muted);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}
.draft-status {
  color: var(--sos-text-tertiary);
  font-size: 12px;
  margin-top: 8px;
}
.primary-action {
  position: sticky;
  bottom: 0;
  background: var(--sos-bg-card);
  padding-top: 12px;
  margin-top: 16px;
  border-top: 1px solid var(--sos-divider);
}
@media (max-width: 1280px) {
  .tri-pane {
    grid-template-columns: 260px minmax(0, 1fr) 280px;
  }
}
</style>
