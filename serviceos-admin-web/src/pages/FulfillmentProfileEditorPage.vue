<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  Select,
  Space,
  Tabs,
  TabPane,
  Tag,
} from 'ant-design-vue'
import { ArrowLeftOutlined, CopyOutlined, SaveOutlined } from '@ant-design/icons-vue'
import ConfigurationPageLayout from '../patterns/templates/ConfigurationPageLayout.vue'
import StickyActionBar from '../patterns/StickyActionBar.vue'
import FulfillmentStageNavigation from '../components/fulfillment/FulfillmentStageNavigation.vue'
import {
  getProjectFulfillmentDraft,
  getProjectFulfillmentProfile,
  updateProjectFulfillmentDraft,
  validateProjectFulfillmentDraft,
  type ProjectFulfillmentDraft,
  type ProjectFulfillmentProfileDetail,
  type ProjectFulfillmentValidationIssue,
} from '../api/fulfillmentProfiles'
import {
  listConfigurationDrafts,
  type ConfigurationDraft,
  type DesignerAssetType,
} from '../api/configurationDrafts'
import { isConflictError } from '../api/client'
import { toUserFacingError } from '../product/errorMessages'
import { labelServiceProduct } from '../presentation/enum-labels'
import { statusLabel } from '../product/statusLabels'

type StageDoc = {
  stageCode: string
  stageName: string
  sequence: number
  stageType?: string
  taskType?: string
  ownerType?: string
  description?: string
  formRefs?: string[]
  evidenceRefs?: string[]
  actions?: Array<Record<string, unknown>>
  transitions?: Array<Record<string, unknown>>
  exceptionPaths?: Array<Record<string, unknown>>
  slaRef?: string | null
  terminal?: boolean
}

type CatalogOption = { value: string; label: string }

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const catalogLoading = ref(false)
const error = ref<string | null>(null)
const conflict = ref(false)
const message = ref<string | null>(null)
const catalogWarning = ref<string | null>(null)
const detail = ref<ProjectFulfillmentProfileDetail | null>(null)
const draft = ref<ProjectFulfillmentDraft | null>(null)
const issues = ref<ProjectFulfillmentValidationIssue[]>([])
const stages = ref<StageDoc[]>([])
const selectedStageCode = ref<string | null>(null)
const profileName = ref('')
const description = ref('')
const activeTab = ref('rules')
const dirty = ref(false)
const catalogs = ref<Record<string, ConfigurationDraft[]>>({})

const allowedActionSet = computed(() => new Set(detail.value?.allowedActions ?? []))
const canEdit = computed(() => allowedActionSet.value.has('EDIT_DRAFT'))
const canValidate = computed(() => allowedActionSet.value.has('VALIDATE'))
const canPreview = computed(() => allowedActionSet.value.has('COMPILE_PREVIEW'))
const canPublish = computed(() => allowedActionSet.value.has('PUBLISH'))

const selectedStage = computed(
  () => stages.value.find((stage) => stage.stageCode === selectedStageCode.value) ?? null,
)

const stageErrorCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {}
  for (const issue of issues.value) {
    if (issue.severity !== 'ERROR' || !issue.stageCode) continue
    counts[issue.stageCode] = (counts[issue.stageCode] ?? 0) + 1
  }
  return counts
})

const blockingIssueCount = computed(
  () => issues.value.filter((issue) => issue.severity === 'ERROR').length,
)
const warningIssueCount = computed(
  () => issues.value.filter((issue) => issue.severity === 'WARNING').length,
)

const taskTypeOptions = [
  { value: 'DISPATCH', label: '派单调度' },
  { value: 'SURVEY', label: '现场勘测' },
  { value: 'INSTALL', label: '上门安装' },
  { value: 'REVIEW', label: '审核处理' },
  { value: 'FIELD_TASK', label: '现场任务' },
]

const ownerOptions = [
  { value: 'PLATFORM', label: '平台运营' },
  { value: 'NETWORK', label: '合作网点' },
  { value: 'TECHNICIAN', label: '服务师傅' },
]

const exceptionOptions = [
  { value: 'MISSING_EVIDENCE', label: '资料缺失' },
  { value: 'WAITING_CUSTOMER', label: '等待客户' },
  { value: 'WAITING_MATERIAL', label: '等待物料' },
  { value: 'UNABLE_TO_WORK', label: '现场无法施工' },
  { value: 'CORRECTION_REQUIRED', label: '需要整改' },
  { value: 'PAUSED', label: '申请暂停' },
  { value: 'CANCELLED', label: '申请取消' },
]

/**
 * M385 过渡：服务端仍返回 documentJson，本页集中解析并在保存时回写。
 * 结构化 Draft DTO 合入后必须删除该适配层，不能长期保留双轨编辑模型。
 */
function parseStages(documentJson: string): StageDoc[] {
  try {
    const doc = JSON.parse(documentJson) as { stages?: StageDoc[] }
    return [...(doc.stages ?? [])].sort((left, right) => left.sequence - right.sequence)
  } catch {
    return []
  }
}

function buildDocumentJson(): string {
  const base = draft.value?.documentJson
    ? (JSON.parse(draft.value.documentJson) as Record<string, unknown>)
    : { schemaVersion: '1.0.0' }
  return JSON.stringify({
    ...base,
    orderTypeName: detail.value ? labelServiceProduct(detail.value.serviceProductCode) : undefined,
    stages: stages.value.map((stage, index) => ({
      ...stage,
      sequence: index + 1,
    })),
  })
}

function assetDisplayName(draftItem: ConfigurationDraft): string {
  try {
    const parsed = JSON.parse(draftItem.definitionJson) as Record<string, unknown>
    const name = parsed.name ?? parsed.title ?? parsed.label
    if (typeof name === 'string' && name.trim()) return name.trim()
  } catch {
    // 资产定义解析失败时使用稳定资产键，技术原文不进入主界面。
  }
  return draftItem.assetKey
}

function catalogOptions(assetType: DesignerAssetType): CatalogOption[] {
  return (catalogs.value[assetType] ?? [])
    .filter((item) => item.status === 'PUBLISHED' && !!item.publishedVersionId)
    .map((item) => ({
      value: item.assetKey,
      label: `${assetDisplayName(item)} · v${item.intendedSemanticVersion}`,
    }))
}

async function loadCatalogs() {
  catalogLoading.value = true
  catalogWarning.value = null
  try {
    const [forms, evidence, sla] = await Promise.all([
      listConfigurationDrafts('FORM'),
      listConfigurationDrafts('EVIDENCE'),
      listConfigurationDrafts('SLA'),
    ])
    catalogs.value = { FORM: forms, EVIDENCE: evidence, SLA: sla }
  } catch {
    catalogs.value = {}
    catalogWarning.value = '配置资产目录暂时不可用。已保留当前绑定，但不能新增表单、资料或 SLA。'
  } finally {
    catalogLoading.value = false
  }
}

async function load() {
  loading.value = true
  error.value = null
  conflict.value = false
  message.value = null
  try {
    detail.value = (await getProjectFulfillmentProfile(projectId.value, profileId.value)).data
    draft.value = (await getProjectFulfillmentDraft(projectId.value, profileId.value)).data
    profileName.value = draft.value.profileName
    description.value = draft.value.description ?? ''
    stages.value = parseStages(draft.value.documentJson)
    selectedStageCode.value = stages.value[0]?.stageCode ?? null
    dirty.value = false
    await loadCatalogs()
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

function markDirty() {
  if (!canEdit.value) return
  dirty.value = true
  message.value = null
}

function addStage() {
  if (!canEdit.value) return
  const code = `STAGE_${Date.now().toString(36).toUpperCase()}`
  stages.value.push({
    stageCode: code,
    stageName: '新阶段',
    sequence: stages.value.length + 1,
    stageType: 'USER_TASK',
    ownerType: 'TECHNICIAN',
    taskType: 'FIELD_TASK',
    formRefs: [],
    evidenceRefs: [],
    actions: [],
    transitions: [],
    exceptionPaths: [],
  })
  selectedStageCode.value = code
  markDirty()
}

function removeStage(code: string) {
  if (!canEdit.value) return
  stages.value = stages.value.filter((stage) => stage.stageCode !== code)
  if (selectedStageCode.value === code) {
    selectedStageCode.value = stages.value[0]?.stageCode ?? null
  }
  markDirty()
}

function moveStage(code: string, delta: number) {
  if (!canEdit.value) return
  const index = stages.value.findIndex((stage) => stage.stageCode === code)
  const target = index + delta
  if (index < 0 || target < 0 || target >= stages.value.length) return
  const copy = [...stages.value]
  const [item] = copy.splice(index, 1)
  copy.splice(target, 0, item)
  stages.value = copy.map((stage, stageIndex) => ({ ...stage, sequence: stageIndex + 1 }))
  markDirty()
}

function updateTargetStage(value: unknown) {
  const stage = selectedStage.value
  if (!stage || !canEdit.value) return
  const target = typeof value === 'string' ? value : ''
  stage.transitions = target ? [{ targetStage: target, kind: 'NORMAL' }] : []
  stage.actions = target
    ? [
        {
          actionCode: 'COMPLETE_STAGE',
          actionLabel: '完成并进入下一阶段',
          targetStage: target,
        },
      ]
    : []
  markDirty()
}

function targetStageValue(): string | undefined {
  const transition = selectedStage.value?.transitions?.[0] as
    | { targetStage?: string }
    | undefined
  return transition?.targetStage || undefined
}

function exceptionValues(): string[] {
  return (selectedStage.value?.exceptionPaths ?? [])
    .map((path) => String((path as { code?: string; trigger?: string }).code ?? (path as { trigger?: string }).trigger ?? ''))
    .filter(Boolean)
}

function updateExceptions(values: unknown) {
  const stage = selectedStage.value
  if (!stage || !canEdit.value) return
  const selected = Array.isArray(values) ? values.map(String) : []
  stage.exceptionPaths = selected.map((code) => ({
    code,
    label: exceptionOptions.find((item) => item.value === code)?.label ?? code,
    trigger: code,
  }))
  markDirty()
}

async function saveDraft(): Promise<boolean> {
  if (!draft.value || !canEdit.value) return false
  saving.value = true
  error.value = null
  conflict.value = false
  try {
    const updated = await updateProjectFulfillmentDraft(
      projectId.value,
      profileId.value,
      draft.value.aggregateVersion,
      {
        profileName: profileName.value.trim(),
        description: description.value.trim(),
        documentJson: buildDocumentJson(),
        workflowAssetVersionId: draft.value.workflowAssetVersionId ?? undefined,
        sourceBundleId: draft.value.sourceBundleId ?? undefined,
      },
    )
    draft.value = updated.data
    dirty.value = false
    message.value = '草稿已保存'
    return true
  } catch (err) {
    if (isConflictError(err)) {
      conflict.value = true
      error.value = '配置已被他人更新。您的本地修改仍保留，请先复制后再重新加载。'
    } else {
      error.value = toUserFacingError(err).message
    }
    return false
  } finally {
    saving.value = false
  }
}

async function validate() {
  if (!canValidate.value) return
  validating.value = true
  error.value = null
  try {
    if (dirty.value && !(await saveDraft())) return
    issues.value = (await validateProjectFulfillmentDraft(projectId.value, profileId.value)).data
    message.value = blockingIssueCount.value
      ? `校验完成：${blockingIssueCount.value} 个阻断错误`
      : '校验通过，可以进入发布流程'
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    validating.value = false
  }
}

function jumpToIssue(issue: ProjectFulfillmentValidationIssue) {
  if (issue.stageCode) {
    selectedStageCode.value = issue.stageCode
    activeTab.value = 'rules'
  }
}

async function copyLocalDraft() {
  try {
    await navigator.clipboard.writeText(buildDocumentJson())
    message.value = '本地修改已复制，可重新加载服务端最新版本'
  } catch {
    error.value = '浏览器未允许复制，请先不要关闭页面。'
  }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch([projectId, profileId], () => load())
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  load()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <ConfigurationPageLayout
    title="履约配置编辑工作区"
    description="按阶段配置责任、表单、资料、动作、审核、SLA 与异常路径。"
  >
    <template #secondary-actions>
      <Button
        @click="
          router.push({
            name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
            params: { id: projectId, profileId },
          })
        "
      >
        <template #icon><ArrowLeftOutlined /></template>
        返回配置详情
      </Button>
    </template>
    <template #primary-action>
      <Space>
        <Button v-if="canValidate" :loading="validating" @click="validate">验证配置</Button>
        <Button
          v-if="canPreview"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW',
              params: { id: projectId, profileId },
            })
          "
        >
          运行预览
        </Button>
        <Button
          v-if="canPublish"
          type="primary"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.PUBLISH',
              params: { id: projectId, profileId },
            })
          "
        >
          发布新版本
        </Button>
      </Space>
    </template>

    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-else-if="message"
        :type="blockingIssueCount ? 'warning' : 'success'"
        show-icon
        :message="message"
        style="margin-bottom: 12px"
      />
      <Alert
        v-if="conflict"
        type="warning"
        show-icon
        message="发现并发修改"
        description="系统没有覆盖您的本地内容。请先复制本地修改，再重新加载服务端最新草稿。"
        style="margin-bottom: 12px"
      >
        <template #action>
          <Space>
            <Button size="small" @click="copyLocalDraft">
              <template #icon><CopyOutlined /></template>
              复制本地修改
            </Button>
            <Button size="small" @click="load">重新加载</Button>
          </Space>
        </template>
      </Alert>
      <Alert
        v-if="catalogWarning"
        type="warning"
        show-icon
        :message="catalogWarning"
        style="margin-bottom: 12px"
      />
      <Alert
        v-if="detail && !canEdit"
        type="info"
        show-icon
        message="当前配置为只读"
        description="当前状态或权限不允许修改草稿。您仍可查看阶段和已绑定的业务资产。"
        style="margin-bottom: 12px"
      />
    </template>

    <template #context>
      <p v-if="detail">
        工单类型：{{ labelServiceProduct(detail.serviceProductCode) }} · 配置状态：{{ statusLabel(detail.status) }}
        · {{ dirty ? '有未保存修改' : '草稿已保存' }}
      </p>
    </template>

    <template #version>
      <p>当前发布版本：{{ detail?.activeVersion || '尚未发布' }}</p>
      <p>阻断错误 {{ blockingIssueCount }} · 警告 {{ warningIssueCount }}</p>
    </template>

    <div v-if="!loading" class="editor-grid">
      <FulfillmentStageNavigation
        :stages="stages"
        :selected-code="selectedStageCode"
        :error-counts="stageErrorCounts"
        :disabled="!canEdit"
        @select="selectedStageCode = $event"
        @add="addStage"
        @move="moveStage"
        @remove="removeStage"
      />

      <section class="editor-grid__main" aria-label="阶段配置">
        <Form v-if="selectedStage" layout="vertical" :disabled="!canEdit">
          <Tabs v-model:activeKey="activeTab">
            <TabPane key="rules" tab="阶段规则">
              <FormItem label="阶段名称" required>
                <Input v-model:value="selectedStage.stageName" @change="markDirty" />
              </FormItem>
              <FormItem label="阶段说明">
                <Input.TextArea
                  v-model:value="selectedStage.description"
                  :rows="3"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="业务任务类型" required>
                <Select
                  v-model:value="selectedStage.taskType"
                  :options="taskTypeOptions"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="主要责任方" required>
                <Select
                  v-model:value="selectedStage.ownerType"
                  :options="ownerOptions"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="阶段性质">
                <Select
                  :value="selectedStage.terminal ? 'TERMINAL' : 'NORMAL'"
                  :options="[
                    { value: 'NORMAL', label: '普通处理阶段' },
                    { value: 'TERMINAL', label: '流程完成阶段' },
                  ]"
                  @change="
                    (value: unknown) => {
                      selectedStage!.terminal = value === 'TERMINAL'
                      selectedStage!.stageType = value === 'TERMINAL' ? 'END' : 'USER_TASK'
                      markDirty()
                    }
                  "
                />
              </FormItem>
            </TabPane>

            <TabPane key="forms" tab="表单">
              <Alert
                type="info"
                show-icon
                message="选择已发布的表单模板"
                description="这里只绑定表单模板；字段设计和高级条件仍在配置中心维护。"
                style="margin-bottom: 12px"
              />
              <FormItem label="本阶段需要填写的表单">
                <Select
                  v-model:value="selectedStage.formRefs"
                  mode="multiple"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  :loading="catalogLoading"
                  :options="catalogOptions('FORM')"
                  placeholder="请选择已发布表单"
                  @change="markDirty"
                />
              </FormItem>
              <Empty
                v-if="!catalogLoading && catalogOptions('FORM').length === 0"
                description="暂无已发布表单模板"
              />
            </TabPane>

            <TabPane key="evidence" tab="资料要求">
              <Alert
                type="info"
                show-icon
                message="选择已发布的资料要求模板"
                description="必传数量、现场拍摄和质量校验规则在资料模板中维护。"
                style="margin-bottom: 12px"
              />
              <FormItem label="本阶段需要提交的资料">
                <Select
                  v-model:value="selectedStage.evidenceRefs"
                  mode="multiple"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  :loading="catalogLoading"
                  :options="catalogOptions('EVIDENCE')"
                  placeholder="请选择资料要求"
                  @change="markDirty"
                />
              </FormItem>
              <Empty
                v-if="!catalogLoading && catalogOptions('EVIDENCE').length === 0"
                description="暂无已发布资料模板"
              />
            </TabPane>

            <TabPane key="actions" tab="动作与流转">
              <Alert
                type="info"
                show-icon
                message="当前先配置正常完成路径"
                description="最终动作是否可执行仍由服务端 allowed-actions 根据人员、状态、表单和资料事实计算。"
                style="margin-bottom: 12px"
              />
              <FormItem label="完成后进入">
                <Select
                  :value="targetStageValue()"
                  allow-clear
                  :options="
                    stages
                      .filter((stage) => stage.stageCode !== selectedStageCode)
                      .map((stage) => ({ value: stage.stageCode, label: stage.stageName }))
                  "
                  placeholder="请选择正常下一阶段"
                  @change="updateTargetStage"
                />
              </FormItem>
              <p class="muted">业务动作：{{ targetStageValue() ? '完成并进入下一阶段' : '尚未配置正常完成动作' }}</p>
            </TabPane>

            <TabPane key="owner" tab="责任与派单">
              <Alert
                type="info"
                show-icon
                message="责任方已在阶段规则中配置"
                description="网点推荐、师傅候选和改派策略将复用已发布的派单规则目录，当前不允许输入人员或组织 ID。"
              />
            </TabPane>

            <TabPane key="review" tab="审核">
              <Alert
                type="info"
                show-icon
                message="审核阶段使用现有审核运行时"
                description="将业务任务类型设为“审核处理”后，该阶段进入 ReviewCase / 整改复审链路。后续将增加审核策略实体选择器。"
              />
            </TabPane>

            <TabPane key="sla" tab="SLA">
              <FormItem label="本阶段 SLA 策略">
                <Select
                  v-model:value="selectedStage.slaRef"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  :loading="catalogLoading"
                  :options="catalogOptions('SLA')"
                  placeholder="请选择已发布 SLA 策略"
                  @change="markDirty"
                />
              </FormItem>
              <Empty
                v-if="!catalogLoading && catalogOptions('SLA').length === 0"
                description="暂无已发布 SLA 策略"
              />
            </TabPane>

            <TabPane key="exceptions" tab="异常路径">
              <FormItem label="本阶段可能出现的异常">
                <Select
                  :value="exceptionValues()"
                  mode="multiple"
                  allow-clear
                  :options="exceptionOptions"
                  placeholder="请选择异常场景"
                  @change="updateExceptions"
                />
              </FormItem>
              <Alert
                type="info"
                show-icon
                message="异常动作和恢复路径仍由服务端运行规则控制"
                description="本页只声明允许出现的标准异常场景，不执行任意脚本或前端自定义状态流转。"
              />
            </TabPane>
          </Tabs>
        </Form>
        <Alert v-else type="info" show-icon message="请选择左侧阶段，或新增阶段开始配置。" />
      </section>

      <aside class="editor-grid__side" aria-label="方案与校验摘要">
        <h3>方案信息</h3>
        <Form layout="vertical" :disabled="!canEdit">
          <FormItem label="方案名称" required>
            <Input v-model:value="profileName" @change="markDirty" />
          </FormItem>
          <FormItem label="业务说明">
            <Input.TextArea v-model:value="description" :rows="3" @change="markDirty" />
          </FormItem>
        </Form>

        <h3>校验结果</h3>
        <ul class="issue-list">
          <li v-for="(issue, index) in issues" :key="`${issue.errorCode}-${index}`">
            <Button type="link" @click="jumpToIssue(issue)">
              <Tag :color="issue.severity === 'ERROR' ? 'error' : 'warning'">
                {{ issue.severity === 'ERROR' ? '阻断' : '警告' }}
              </Tag>
              {{ issue.userMessage }}
            </Button>
          </li>
          <li v-if="!issues.length" class="muted">尚未校验或未发现问题</li>
        </ul>
      </aside>
    </div>

    <StickyActionBar :note="dirty ? '当前有未保存修改' : '草稿已保存'">
      <Button @click="load">重新加载</Button>
      <Button v-if="canEdit" type="primary" :loading="saving" @click="saveDraft">
        <template #icon><SaveOutlined /></template>
        保存草稿
      </Button>
      <Button v-if="canValidate" :loading="validating" @click="validate">验证配置</Button>
    </StickyActionBar>
  </ConfigurationPageLayout>
</template>

<style scoped>
.editor-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 300px;
  gap: 16px;
  min-height: 520px;
}
.editor-grid__side,
.editor-grid__main {
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 16px;
  background: var(--sos-color-surface-card, #fff);
}
.editor-grid__side h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.editor-grid__side h3:not(:first-child) {
  margin-top: 20px;
}
.issue-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.issue-list :deep(.ant-btn) {
  height: auto;
  padding: 4px 0;
  white-space: normal;
  text-align: left;
}
.muted {
  color: var(--sos-color-text-secondary);
}
@media (max-width: 1100px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
