<script setup lang="ts">
import type {
  CreateProjectFulfillmentProfileInput,
  ProjectFulfillmentMatchResult,
  ProjectFulfillmentRunbookStage,
  ProjectFulfillmentStageDraft,
} from '@serviceos/api-client'
import {
  compileProjectFulfillmentPreview,
  loadProjectFulfillmentRevisions,
  simulateProjectFulfillmentMatch,
} from '@serviceos/api-client'
import {
  Button,
  Drawer,
  Form,
  Input,
  Select,
  Tag,
} from '@serviceos/design-system'
import { useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import BlueprintSidebar from '../../../components/serviceos/BlueprintSidebar.vue'
import FormDesigner from '../../../components/serviceos/FormDesigner.vue'
import SlaRulePanel from '../../../components/serviceos/SlaRulePanel.vue'
import TaskTemplatePanel from '../../../components/serviceos/TaskTemplatePanel.vue'
import VersionTimeline from '../../../components/serviceos/VersionTimeline.vue'
import WorkflowCanvas from '../../../components/serviceos/WorkflowCanvas.vue'
import WorkflowNodePanel from '../../../components/serviceos/WorkflowNodePanel.vue'
import type { SlaRuleItem } from '../../../components/serviceos/SlaRulePanel.vue'
import type { TaskTemplateItem } from '../../../components/serviceos/TaskTemplatePanel.vue'
import type { VersionTimelineItem } from '../../../components/serviceos/VersionTimeline.vue'
import VersionBadge from '../../../components/serviceos/VersionBadge.vue'
import type { WorkflowCanvasStage } from '../../../components/serviceos/types'
import { formatDateTime } from '../../../presenters/work-order'
import { presentServiceProduct, presentTaskType, presentWorkflowOwner } from '../presenters/project-operations'
import { useCreateProjectFulfillmentCommand } from '../commands/use-create-project-fulfillment-command'
import {
  useProjectFulfillmentProfileQuery,
  useProjectFulfillmentProfilesQuery,
  useProjectFulfillmentDraftQuery,
} from '../queries/use-project-fulfillment-query'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

type BlueprintSection =
  | 'base'
  | 'flow'
  | 'stages'
  | 'tasks'
  | 'forms'
  | 'evidence'
  | 'sla'
  | 'acceptance'
  | 'settlement'
  | 'notifications'
  | 'simulation'
  | 'versions'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const project = useProjectWorkspaceQuery(projectId)
const profiles = useProjectFulfillmentProfilesQuery(projectId)
const selectedProfileId = ref<string>()
const selectedProfile = useProjectFulfillmentProfileQuery(projectId, selectedProfileId)
const selectedDraftProfileId = computed(() => selectedProfileId.value ?? '')
const draft = useProjectFulfillmentDraftQuery(projectId, selectedDraftProfileId)
const preview = useQuery({
  queryKey: computed(() => ['project-fulfillment-preview', projectId.value, selectedProfileId.value]),
  queryFn: () => compileProjectFulfillmentPreview(projectId.value, selectedProfileId.value!),
  enabled: computed(() => Boolean(projectId.value && selectedProfileId.value)),
  retry: false,
})
const revisions = useQuery({
  queryKey: computed(() => ['project-fulfillment-revisions', projectId.value, selectedProfileId.value]),
  queryFn: () => loadProjectFulfillmentRevisions(projectId.value, selectedProfileId.value!),
  enabled: computed(() => Boolean(projectId.value && selectedProfileId.value)),
})

const selectedSummary = computed(() => profiles.data.value?.find((item) => item.profileId === selectedProfileId.value))
const selectedStageCode = ref<string>()
const sections: Array<{ key: BlueprintSection; label: string; detail: string }> = [
  { key: 'base', label: '基础规则', detail: '服务场景与适用范围' },
  { key: 'flow', label: '流程设计', detail: '节点顺序与流转关系' },
  { key: 'stages', label: '阶段管理', detail: '阶段责任与引用关系' },
  { key: 'tasks', label: '任务模板', detail: '输入、输出与完成条件' },
  { key: 'forms', label: '表单设计', detail: '字段库与表单预览' },
  { key: 'evidence', label: '证据规则', detail: '资料引用与审核依据' },
  { key: 'sla', label: 'SLA 规则', detail: '目标、预警与升级' },
  { key: 'acceptance', label: '验收规则', detail: '验收条件与责任边界' },
  { key: 'settlement', label: '结算规则', detail: '结算条件与输出' },
  { key: 'notifications', label: '通知规则', detail: '业务通知与升级' },
  { key: 'simulation', label: '模拟运行', detail: '验证方案匹配结果' },
  { key: 'versions', label: '版本管理', detail: '历史、校验与发布' },
]
const activeSection = computed<BlueprintSection>(() => {
  const requested = String(route.query.section ?? 'flow') as BlueprintSection
  return sections.some((section) => section.key === requested) ? requested : 'flow'
})

const createOpen = ref(false)
const createdProfileName = ref<string>()
const initializationMode = ref('TEMPLATE:HOME_CHARGING_SURVEY_INSTALL')
const form = reactive<CreateProjectFulfillmentProfileInput>({
  matchPriority: 0,
  profileCode: '',
  profileName: '',
  serviceProductCode: '',
  description: '',
})
const createCommand = useCreateProjectFulfillmentCommand(() => projectId.value)
const simulationOpen = ref(false)
const simulationLoading = ref(false)
const simulationError = ref<string>()
const simulationResult = ref<ProjectFulfillmentMatchResult>()
const simulationForm = reactive({
  brandCode: 'BYD_OCEAN',
  provinceCode: '370000',
  serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
})
const selectedRevision = ref<VersionTimelineItem>()
const initializationOptions = computed(() => [
  {
    value: 'TEMPLATE:HOME_CHARGING_SURVEY_INSTALL',
    label: '家充勘测安装标准流程',
  },
  {
    value: 'TEMPLATE:BLANK',
    label: '空白方案',
  },
  ...(profiles.data.value ?? []).map((profile) => ({
    value: `COPY:${profile.profileId}`,
    label: `复制“${profile.profileName}”`,
  })),
])

watch(
  () => profiles.data.value,
  (items) => {
    if (!items?.length) {
      selectedProfileId.value = undefined
      return
    }
    const requested = typeof route.query.profileId === 'string' ? route.query.profileId : undefined
    selectedProfileId.value = items.some((item) => item.profileId === requested)
      ? requested
      : items[0]?.profileId
  },
  { immediate: true },
)

const previewStages = computed(() => preview.data.value?.runbook.stages ?? [])
const canvasStages = computed<WorkflowCanvasStage[]>(() => {
  const draftStages = draft.data.value?.document.stages ?? []
  if (draftStages.length) return draftStages.map(toCanvasStage)
  if (previewStages.value.length) return previewStages.value.map(toPreviewCanvasStage)

  const summaryNames = selectedSummary.value?.workflowSummary
    ?.split(/\s*(?:→|->|＞|>|\/|、|，|,)\s*/)
    .filter(Boolean) ?? []
  const count = summaryNames.length || selectedSummary.value?.stageCount || 0
  return summaryNames.slice(0, count).map((name, index) => ({
    code: `summary-stage-${index + 1}`,
    name,
    sequence: index + 1,
    ownerLabel: '接口未返回',
    taskLabel: '接口未返回',
    typeLabel: '履约阶段',
    slaLabel: selectedSummary.value?.slaSummary ?? '接口未返回',
    formCount: 0,
    evidenceCount: 0,
  }))
})
const selectedStage = computed(() => canvasStages.value.find((stage) => stage.code === selectedStageCode.value))
const taskTemplates = computed<TaskTemplateItem[]>(() => canvasStages.value.map((stage) => {
  const runbookStage = previewStages.value.find((item) => item.sequence === stage.sequence)
  return {
    id: `task-${stage.code}`,
    name: runbookStage?.taskTypeLabel ?? stage.taskLabel,
    owner: runbookStage?.ownerTypeLabel ?? stage.ownerLabel,
    input: runbookStage?.actionSummary ?? '接口未返回输入定义',
    output: runbookStage?.nextStageSummary ?? '接口未返回输出定义',
    sla: runbookStage?.slaSummary ?? stage.slaLabel,
    form: runbookStage ? `${runbookStage.formCount ?? stage.formCount} 份引用` : `${stage.formCount} 份引用`,
    evidence: runbookStage ? `${runbookStage.evidenceCount ?? stage.evidenceCount} 项引用` : `${stage.evidenceCount} 项引用`,
    completion: runbookStage?.nextStageSummary ?? '接口未返回完成条件',
    stage: `阶段 ${String(stage.sequence).padStart(2, '0')} · ${stage.name}`,
  }
}))
const slaRules = computed<SlaRuleItem[]>(() => canvasStages.value.map((stage) => {
  const runbookStage = previewStages.value.find((item) => item.sequence === stage.sequence)
  return {
    id: `sla-${stage.code}`,
    task: runbookStage?.taskTypeLabel ?? stage.taskLabel,
    target: runbookStage?.slaSummary ?? stage.slaLabel,
    warning: '接口未返回',
    escalation: '接口未返回',
    basis: '接口未返回',
  }
}))
const evidenceItems = computed(() => canvasStages.value.map((stage) => {
  const source = draft.data.value?.document.stages.find((item) => item.stageCode === stage.code)
  const runbookStage = previewStages.value.find((item) => item.sequence === stage.sequence)
  return {
    id: stage.code,
    stage: stage.name,
    count: runbookStage?.evidenceCount ?? stage.evidenceCount,
    summary: runbookStage?.evidenceSummary ?? (source?.evidenceRefs?.length ? '已绑定引用' : '未绑定证据'),
    refs: source?.evidenceRefs ?? [],
  }
}))
const isDraftEditable = computed(() => Boolean(selectedProfile.data.value?.draftRevisionId))
const lastModifiedLabel = computed(() => selectedSummary.value?.updatedAt ? formatDateTime(selectedSummary.value.updatedAt) : '待同步')
const sidebarPlans = computed(() => (profiles.data.value ?? []).map((profile) => ({
  id: profile.profileId,
  name: profile.profileName,
  productLabel: presentServiceProduct(profile.serviceProductCode),
  status: profile.status,
  statusClass: profile.status.toLowerCase(),
  version: profile.activeVersion,
  stageCount: profile.stageCount,
})))
const versionItems = computed<VersionTimelineItem[]>(() => (revisions.data.value ?? [])
  .slice()
  .sort((left, right) => right.versionNo - left.versionNo)
  .map((revision) => {
    const active = selectedProfile.data.value?.activeVersion === String(revision.versionNo)
    return {
      id: revision.revisionId,
      version: String(revision.versionNo),
      status: active ? 'active' : 'historical',
      statusLabel: active ? '当前生效' : '历史版本',
      date: formatDateTime(revision.publishedAt ?? revision.createdAt),
      author: revision.publishedByDisplayName ?? '系统记录',
      summary: active
        ? '当前用于新工单匹配；进行中的工单继续使用创建时绑定版本。'
        : '不可变历史版本，可查看并复制为新的活动草稿。',
      active,
    }
  }))
const unavailableSection = computed(() => {
  const section = sections.find((item) => item.key === activeSection.value)
  return {
    label: section?.label ?? '规则',
    detail: section?.detail ?? '规则配置',
  }
})

watch(
  canvasStages,
  (stages) => {
    if (!stages.some((stage) => stage.code === selectedStageCode.value)) selectedStageCode.value = stages[0]?.code
  },
  { immediate: true },
)

function toCanvasStage(stage: ProjectFulfillmentStageDraft): WorkflowCanvasStage {
  return {
    code: stage.stageCode,
    name: stage.stageName,
    sequence: stage.sequence,
    ownerLabel: presentWorkflowOwner(stage.ownerType),
    taskLabel: presentTaskType(stage.taskType),
    typeLabel: stage.terminal ? '完成阶段' : stage.stageType === 'REVIEW_TASK' ? '审核任务' : stage.stageType === 'WAIT_EVENT' ? '等待事件' : '履约阶段',
    slaLabel: stage.slaRef ? '已绑定 SLA 引用' : '未绑定 SLA',
    formCount: stage.formRefs?.length ?? 0,
    evidenceCount: stage.evidenceRefs?.length ?? 0,
    description: stage.description,
    terminal: stage.terminal,
  }
}

function toPreviewCanvasStage(stage: ProjectFulfillmentRunbookStage): WorkflowCanvasStage {
  return {
    code: `runbook-stage-${stage.sequence}`,
    name: stage.stageName,
    sequence: stage.sequence,
    ownerLabel: stage.ownerTypeLabel || '接口未返回',
    taskLabel: stage.taskTypeLabel || '接口未返回',
    typeLabel: '履约阶段',
    slaLabel: stage.slaSummary ?? '接口未返回',
    formCount: stage.formCount ?? 0,
    evidenceCount: stage.evidenceCount ?? 0,
    description: stage.actionSummary,
    terminal: stage.terminal,
  }
}

function selectProfile(profileId: string) {
  selectedProfileId.value = profileId
  void router.replace({ query: { ...route.query, profileId } })
}

function selectSection(section: BlueprintSection) {
  void router.replace({ query: { ...route.query, section } })
}

function selectSidebarSection(section: string) {
  if (sections.some((item) => item.key === section)) selectSection(section as BlueprintSection)
}

function openRevisionCopy(item: VersionTimelineItem) {
  selectedRevision.value = {
    ...item,
    summary: '已选择该历史版本；进入活动草稿后，保存会形成新的不可变版本，历史版本不会被修改。',
  }
  if (selectedProfile.data.value?.draftRevisionId) {
    void router.push(`/projects/${projectId.value}/fulfillment/${selectedProfileId.value}/draft`)
  }
}

function resetCreateForm() {
  form.profileName = ''
  form.profileCode = ''
  form.serviceProductCode = ''
  form.matchPriority = 0
  form.description = ''
  initializationMode.value = 'TEMPLATE:HOME_CHARGING_SURVEY_INSTALL'
  createCommand.reset()
}

function closeCreate() {
  createOpen.value = false
  resetCreateForm()
}

function openSimulation() {
  simulationForm.serviceProductCode = selectedSummary.value?.serviceProductCode ?? 'HOME_CHARGING_SURVEY_INSTALL'
  simulationResult.value = undefined
  simulationError.value = undefined
  simulationOpen.value = true
}

function closeSimulation() {
  simulationOpen.value = false
  simulationResult.value = undefined
  simulationError.value = undefined
}

async function runSimulation() {
  simulationLoading.value = true
  simulationError.value = undefined
  simulationResult.value = undefined
  try {
    simulationResult.value = await simulateProjectFulfillmentMatch(projectId.value, {
      brandCode: simulationForm.brandCode.trim().toUpperCase() || undefined,
      provinceCode: simulationForm.provinceCode.trim() || undefined,
      serviceProductCode: simulationForm.serviceProductCode.trim().toUpperCase(),
    })
  } catch (error) {
    simulationError.value = error instanceof Error ? error.message : '模拟匹配失败'
  } finally {
    simulationLoading.value = false
  }
}

async function createProfile() {
  const copyFromProfileId = initializationMode.value.startsWith('COPY:')
    ? initializationMode.value.slice('COPY:'.length)
    : undefined
  const templateCode = initializationMode.value.startsWith('TEMPLATE:')
    ? initializationMode.value.slice('TEMPLATE:'.length) as 'BLANK' | 'HOME_CHARGING_SURVEY_INSTALL'
    : undefined
  const created = await createCommand.mutateAsync({
    copyFromProfileId,
    matchPriority: Number(form.matchPriority ?? 0),
    profileCode: form.profileCode?.trim().toUpperCase(),
    profileName: form.profileName.trim(),
    serviceProductCode: form.serviceProductCode.trim().toUpperCase(),
    templateCode,
    description: form.description?.trim() || undefined,
  })
  createdProfileName.value = created.profileName
  selectedProfileId.value = created.profileId
  closeCreate()
  await router.replace({ query: { profileId: created.profileId, section: 'flow' } })
}
</script>

<template>
  <div class="fulfillment-blueprint-page">
    <header class="fulfillment-blueprint-header">
      <div>
        <p class="breadcrumb">客户与项目 / {{ project.data.value?.projectName ?? '项目' }} / 履约方案设计</p>
        <div class="fulfillment-blueprint-header__title-line">
          <h1>{{ selectedSummary?.profileName ?? '履约方案设计' }}</h1>
          <VersionBadge v-if="selectedProfile.data.value" :status="selectedProfile.data.value.status" :version="selectedProfile.data.value.activeVersion" />
        </div>
        <p>流程、责任、任务、表单、证据和 SLA 作为一个方案版本设计与发布。</p>
      </div>
      <div class="heading-actions">
        <RouterLink class="secondary-link-button" :to="`/projects/${projectId}`">返回项目概览</RouterLink>
        <Button @click="openSimulation">模拟运行</Button>
        <RouterLink v-if="selectedProfile.data.value?.draftRevisionId" class="secondary-link-button" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">继续编辑草稿</RouterLink>
        <Button v-else disabled title="当前没有活动草稿，创建版本需要先从方案草稿入口开始">创建新版本</Button>
        <RouterLink v-if="selectedProfile.data.value?.draftRevisionId" class="primary-link-button" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/publish`">发布版本</RouterLink>
        <Button type="primary" @click="createOpen = true">新建方案</Button>
      </div>
    </header>

    <div v-if="createdProfileName" class="product-notice success">已创建履约方案“{{ createdProfileName }}”。</div>
    <PageError v-if="profiles.isError.value" :detail="profiles.error.value?.message ?? '履约方案加载失败'" />
    <div v-else-if="profiles.isLoading.value" class="page-loading">正在加载履约方案…</div>
    <section v-else class="fulfillment-blueprint-layout">
      <BlueprintSidebar
        :plans="sidebarPlans"
        :sections="sections"
        :selected-plan-id="selectedProfileId"
        :active-section="activeSection"
        @select-plan="selectProfile"
        @select-section="selectSidebarSection"
      >
        <template #footer>
          <VersionBadge :status="selectedProfile.data.value?.status" :version="selectedProfile.data.value?.activeVersion" compact />
          <RouterLink v-if="selectedProfile.data.value?.draftRevisionId" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">编辑活动草稿</RouterLink>
          <RouterLink v-if="selectedProfile.data.value" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/publish`">打开发布检查</RouterLink>
        </template>
      </BlueprintSidebar>

      <main class="blueprint-canvas-column">
        <PageError v-if="selectedProfile.isError.value" :detail="selectedProfile.error.value?.message ?? '方案详情加载失败'" />
        <div v-else-if="selectedProfile.isLoading.value" class="page-loading">正在读取方案…</div>
        <template v-else-if="selectedProfile.data.value && selectedSummary">
          <header class="blueprint-main-heading">
            <div>
              <span class="sos-eyebrow">{{ presentServiceProduct(selectedSummary.serviceProductCode) }}</span>
              <h2>{{ selectedSummary.profileName }}</h2>
            </div>
            <dl>
              <div><dt>方案状态</dt><dd>{{ selectedProfile.data.value.status === 'ACTIVE' ? '当前生效' : selectedProfile.data.value.status }}</dd></div>
              <div><dt>最近修改</dt><dd>{{ lastModifiedLabel }}</dd></div>
              <div><dt>匹配优先级</dt><dd>{{ selectedSummary.matchPriority }}</dd></div>
            </dl>
          </header>

          <section v-if="activeSection === 'flow'" class="blueprint-design-surface">
            <div v-if="draft.isLoading.value || preview.isLoading.value" class="blueprint-loading-line">正在读取流程资产…</div>
            <PageError v-if="preview.isError.value && !canvasStages.length" :detail="preview.error.value?.message ?? '流程预览暂不可用'" />
            <WorkflowCanvas :stages="canvasStages" :selected-code="selectedStageCode" readonly @select="selectedStageCode = $event.code" />
            <footer class="blueprint-surface-footer"><span>{{ isDraftEditable ? '当前存在活动草稿，修改请进入草稿编辑。' : '当前展示已发布版本或方案摘要。' }}</span><RouterLink v-if="isDraftEditable" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">打开草稿设计</RouterLink></footer>
          </section>

          <section v-else-if="activeSection === 'base'" class="blueprint-summary-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">基础规则</span><h3>服务场景与适用范围</h3></div><Tag color="processing">优先级 {{ selectedSummary.matchPriority }}</Tag></header>
            <dl class="blueprint-fact-grid"><div><dt>方案编码</dt><dd>{{ selectedProfile.data.value.profileCode }}</dd></div><div><dt>服务产品</dt><dd>{{ presentServiceProduct(selectedSummary.serviceProductCode) }}</dd></div><div><dt>活动草稿</dt><dd>{{ selectedProfile.data.value.draftRevisionId ? '存在未发布草稿' : '无活动草稿' }}</dd></div><div><dt>最近更新</dt><dd>{{ lastModifiedLabel }}</dd></div></dl>
            <div class="blueprint-unavailable-note">适用品牌、省域和其他结构化匹配条件在活动草稿中维护；当前接口未返回的条件不在这里猜测。</div>
          </section>

          <section v-else-if="activeSection === 'stages'" class="blueprint-summary-surface blueprint-stage-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">阶段管理</span><h3>阶段与责任</h3></div><Tag color="processing">{{ canvasStages.length }} 个阶段</Tag></header>
            <ol v-if="canvasStages.length" class="blueprint-stage-list">
              <li v-for="stage in canvasStages" :key="stage.code" :class="{ active: stage.code === selectedStageCode }" @click="selectedStageCode = stage.code">
                <span class="blueprint-stage-list__number">{{ String(stage.sequence).padStart(2, '0') }}</span>
                <div><strong>{{ stage.name }}</strong><small>{{ stage.ownerLabel }} · {{ stage.taskLabel }}</small></div>
                <span>{{ stage.formCount }} 表单 · {{ stage.evidenceCount }} 证据</span>
              </li>
            </ol>
            <div v-else class="sos-inline-unavailable"><strong>阶段数据暂不可用</strong><span>进入活动草稿查看或维护流程阶段。</span></div>
          </section>

          <TaskTemplatePanel v-else-if="activeSection === 'tasks'" :tasks="taskTemplates" />
          <FormDesigner v-else-if="activeSection === 'forms'" :fields="[]" :form-name="`${selectedSummary.profileName} · 表单设计`" unavailable-reason="现有方案接口只返回表单引用与数量，未返回字段定义。进入活动草稿维护结构化表单。" />
          <SlaRulePanel v-else-if="activeSection === 'sla'" :rules="slaRules" />

          <section v-else-if="activeSection === 'evidence'" class="blueprint-summary-surface blueprint-evidence-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">证据规则</span><h3>阶段证据引用</h3></div><Tag color="processing">{{ selectedSummary.evidenceCount }} 项引用</Tag></header>
            <table v-if="evidenceItems.length" class="blueprint-reference-table"><thead><tr><th>阶段</th><th>数量</th><th>摘要</th><th>引用</th></tr></thead><tbody><tr v-for="item in evidenceItems" :key="item.id"><td>{{ item.stage }}</td><td>{{ item.count }}</td><td>{{ item.summary }}</td><td>{{ item.refs.length ? item.refs.join('、') : '接口未返回具体引用' }}</td></tr></tbody></table>
            <div v-else class="sos-inline-unavailable"><strong>证据规则暂不可用</strong><span>进入活动草稿查看并维护证据引用。</span></div>
          </section>

          <section v-else-if="activeSection === 'simulation'" class="blueprint-simulation-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">模拟运行</span><h3>验证方案匹配</h3><p>使用正式匹配条件验证客户品牌、省域和服务产品的命中结果。</p></div><Button type="primary" @click="openSimulation">打开模拟器</Button></header>
            <dl><div><dt>服务产品</dt><dd>{{ presentServiceProduct(selectedSummary.serviceProductCode) }}</dd></div><div><dt>适用范围</dt><dd>以草稿中的结构化条件为准</dd></div><div><dt>当前版本</dt><dd>{{ selectedProfile.data.value.activeVersion ? `V${selectedProfile.data.value.activeVersion}` : '尚未发布' }}</dd></div></dl>
          </section>

          <section v-else-if="activeSection === 'versions'" class="blueprint-summary-surface blueprint-version-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">版本管理</span><h3>历史与发布边界</h3></div><RouterLink v-if="selectedProfile.data.value.draftRevisionId" class="primary-link-button" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/publish`">进入发布检查</RouterLink></header>
            <VersionTimeline :items="versionItems" :editable="Boolean(selectedProfile.data.value.draftRevisionId)" @view="selectedRevision = $event" @copy="openRevisionCopy" />
            <div v-if="selectedRevision" class="blueprint-revision-note"><strong>V{{ selectedRevision.version }} · {{ selectedRevision.statusLabel }}</strong><span>{{ selectedRevision.summary }}</span></div>
          </section>

          <section v-else class="blueprint-unavailable-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">{{ unavailableSection.label }}</span><h3>{{ unavailableSection.label }}</h3></div></header>
            <div class="sos-inline-unavailable"><strong>当前接口未返回{{ unavailableSection.label }}明细</strong><span>{{ unavailableSection.detail }}需进入活动草稿与方案版本一起维护。</span><RouterLink v-if="isDraftEditable" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">进入活动草稿</RouterLink></div>
          </section>
        </template>
        <div v-else class="blueprint-main-empty"><strong>请选择一套履约方案</strong><span>方案流程、责任和版本关系会在这里展示。</span></div>
      </main>

      <WorkflowNodePanel :stage="selectedStage" :stage-count="canvasStages.length" :active-section="activeSection" />
    </section>

    <Drawer :open="createOpen" width="520" title="新建履约方案" @close="closeCreate">
      <p class="create-user-intro">创建方案后仍需完成蓝图设计、校验和发布，才会用于新工单。</p>
      <PageError v-if="createCommand.isError.value" :detail="createCommand.error.value?.message ?? '履约方案创建失败'" />
      <Form layout="vertical">
        <Form.Item label="方案名称" required><Input v-model:value="form.profileName" placeholder="例如：家充勘测与安装" :maxlength="200" /></Form.Item>
        <Form.Item label="服务产品编码" required><Input v-model:value="form.serviceProductCode" placeholder="例如：HOME_CHARGING_INSTALL" :maxlength="96" /></Form.Item>
        <Form.Item label="方案编码" required><Input v-model:value="form.profileCode" placeholder="例如：BYD_SHANDONG_HOME_STANDARD" :maxlength="96" /></Form.Item>
        <Form.Item label="匹配优先级" required><Input v-model:value="form.matchPriority" type="number" :min="-10000" :max="10000" /></Form.Item>
        <Form.Item label="初始化方式" required><Select v-model:value="initializationMode" :options="initializationOptions" /></Form.Item>
        <Form.Item label="方案说明"><Input.TextArea v-model:value="form.description" :rows="4" placeholder="说明适用业务范围" /></Form.Item>
      </Form>
      <template #footer><div class="drawer-footer"><Button @click="closeCreate">取消</Button><Button type="primary" :disabled="!form.profileName.trim() || !form.profileCode?.trim() || !form.serviceProductCode.trim()" :loading="createCommand.isPending.value" @click="createProfile">创建方案</Button></div></template>
    </Drawer>

    <Drawer :open="simulationOpen" width="560" title="履约方案匹配模拟器" @close="closeSimulation">
      <p class="create-user-intro">模拟不会创建工单或运行实例。</p>
      <PageError v-if="simulationError" :detail="simulationError" />
      <Form layout="vertical"><Form.Item label="服务产品编码" required><Input v-model:value="simulationForm.serviceProductCode" :maxlength="96" /></Form.Item><Form.Item label="客户品牌编码"><Input v-model:value="simulationForm.brandCode" placeholder="例如：BYD_OCEAN" :maxlength="64" /></Form.Item><Form.Item label="省级行政区编码"><Input v-model:value="simulationForm.provinceCode" placeholder="例如：370000" :maxlength="6" /></Form.Item></Form>
      <section v-if="simulationResult" class="fulfillment-simulation-result"><header><div><span>唯一命中</span><h3>{{ simulationResult.profileName }}</h3></div><Tag color="success">V{{ simulationResult.fulfillmentVersion }}</Tag></header><dl><div><dt>方案编码</dt><dd>{{ simulationResult.profileCode }}</dd></div><div><dt>匹配优先级</dt><dd>{{ simulationResult.matchPriority }}</dd></div><div><dt>规则具体度</dt><dd>{{ simulationResult.matchSpecificity }}</dd></div><div><dt>配置包版本</dt><dd>{{ simulationResult.configurationBundleVersion }}</dd></div></dl><div class="fulfillment-simulation-reasons"><strong>命中解释</strong><Tag v-for="reason in simulationResult.matchExplanation" :key="reason" color="processing">{{ reason }}</Tag></div></section>
      <template #footer><div class="drawer-footer"><Button @click="closeSimulation">关闭</Button><Button type="primary" :disabled="!simulationForm.serviceProductCode.trim()" :loading="simulationLoading" @click="runSimulation">开始模拟</Button></div></template>
    </Drawer>
  </div>
</template>
