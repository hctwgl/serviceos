<script setup lang="ts">
import type {
  CreateProjectFulfillmentProfileInput,
  ProjectFulfillmentMatchResult,
  ProjectFulfillmentStageDraft,
} from '@serviceos/api-client'
import {
  loadAdminWorkOrders,
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
import ProjectMetricCard from '../../../components/serviceos/ProjectMetricCard.vue'
import SlaPanel from '../../../components/serviceos/SlaPanel.vue'
import TaskTemplatePanel from '../../../components/serviceos/TaskTemplatePanel.vue'
import VersionTimeline from '../../../components/serviceos/VersionTimeline.vue'
import WorkflowDesigner from '../../../components/serviceos/WorkflowDesigner.vue'
import type { FormFieldItem } from '../../../components/serviceos/FormDesigner.vue'
import type { SlaRuleItem } from '../../../components/serviceos/SlaPanel.vue'
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

type BlueprintSection = 'base' | 'flow' | 'tasks' | 'forms' | 'evidence' | 'sla' | 'versions'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const project = useProjectWorkspaceQuery(projectId)
const profiles = useProjectFulfillmentProfilesQuery(projectId)
const workOrders = useQuery({
  queryKey: computed(() => ['project-fulfillment-workorders', projectId.value]),
  queryFn: () => loadAdminWorkOrders({ projectId: projectId.value, limit: 100 }),
  enabled: computed(() => Boolean(projectId.value)),
})
const selectedProfileId = ref<string>()
const selectedProfile = useProjectFulfillmentProfileQuery(projectId, selectedProfileId)
const selectedDraftProfileId = computed(() => selectedProfileId.value ?? '')
const draft = useProjectFulfillmentDraftQuery(projectId, selectedDraftProfileId)
const revisions = useQuery({
  queryKey: computed(() => ['project-fulfillment-revisions', projectId.value, selectedProfileId.value]),
  queryFn: () => loadProjectFulfillmentRevisions(projectId.value, selectedProfileId.value!),
  enabled: computed(() => Boolean(projectId.value && selectedProfileId.value)),
})

const selectedSummary = computed(() => (
  profiles.data.value?.find((item) => item.profileId === selectedProfileId.value)
))
const selectedStageCode = ref<string>()
const sections: Array<{ key: BlueprintSection; label: string; detail: string }> = [
  { key: 'base', label: '基础信息', detail: '服务场景与适用范围' },
  { key: 'flow', label: '流程设计', detail: '履约阶段与流转关系' },
  { key: 'tasks', label: '任务模板', detail: '责任、任务与动作' },
  { key: 'forms', label: '表单设计', detail: '业务采集与校验' },
  { key: 'evidence', label: '证据规则', detail: '资料槽位与审核依据' },
  { key: 'sla', label: 'SLA 规则', detail: '时效与预约承诺' },
  { key: 'versions', label: '版本管理', detail: '校验、影响与发布' },
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
const initializationOptions = computed(() => [
  {
    value: 'TEMPLATE:HOME_CHARGING_SURVEY_INSTALL',
    label: '家充勘测安装标准流程（需后续绑定运行资产）',
  },
  {
    value: 'TEMPLATE:BLANK',
    label: '空白配置（适合新服务场景）',
  },
  ...(profiles.data.value ?? []).map((profile) => ({
    value: `COPY:${profile.profileId}`,
    label: `复制“${profile.profileName}”及其运行绑定`,
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

const canvasStages = computed<WorkflowCanvasStage[]>(() => {
  const stages = draft.data.value?.document.stages ?? []
  if (stages.length) return stages.map(toCanvasStage)
  const summaryStages = selectedSummary.value?.workflowSummary
    ? selectedSummary.value.workflowSummary.split(/\s*(?:→|->|＞|>|\/|、|，|,)\s*/).filter(Boolean)
    : []
  const count = summaryStages.length || selectedSummary.value?.stageCount || 0
  return Array.from({ length: count }, (_, index) => ({
    code: `summary-stage-${index + 1}`,
    name: summaryStages[index] ?? `履约阶段 ${String(index + 1).padStart(2, '0')}`,
    sequence: index + 1,
    ownerLabel: '按方案配置',
    taskLabel: '运行任务',
    typeLabel: '履约阶段',
    slaLabel: selectedSummary.value?.slaSummary ?? '由生效版本提供',
    formCount: index === 0 ? selectedSummary.value?.formCount ?? 0 : 0,
    evidenceCount: index === 0 ? selectedSummary.value?.evidenceCount ?? 0 : 0,
    status: 'pending',
  }))
})
const selectedStage = computed(() => canvasStages.value.find((stage) => stage.code === selectedStageCode.value))
const isDraftEditable = computed(() => Boolean(selectedProfile.data.value?.draftRevisionId))
const taskTemplates = ref<TaskTemplateItem[]>([])
const formFields = ref<FormFieldItem[]>([])
const slaRules = ref<SlaRuleItem[]>([])
const selectedRevision = ref<VersionTimelineItem>()

const runOrderCount = computed(() => {
  const count = project.data.value?.activeWorkOrderCount
  return count === null || count === undefined ? '待同步' : `${count} 单`
})
const fulfillmentSuccessRate = computed(() => {
  const data = workOrders.data.value
  if (!data) return { value: '待同步', hint: '等待项目工单投影' }
  if (data.nextCursor) return { value: '—', hint: '工单量超过展示上限，暂不推算' }
  const completedCount = data.items.filter((item) => item.statusName === '已完成').length
  const value = data.totalCount === 0 ? '—' : `${Math.round((completedCount / data.totalCount) * 100)}%`
  return { value, hint: `${completedCount}/${data.totalCount} 单已完成` }
})
const lastModifiedLabel = computed(() => {
  const updatedAt = selectedSummary.value?.updatedAt
  return updatedAt ? formatDateTime(updatedAt) : '待同步'
})

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

watch(
  canvasStages,
  (stages) => {
    if (!stages.some((stage) => stage.code === selectedStageCode.value)) {
      selectedStageCode.value = stages[0]?.code
    }
  },
  { immediate: true },
)

watch(
  [selectedProfileId, canvasStages],
  ([profileId, stages]) => {
    if (!profileId || !stages.length) return
    const firstTaskId = taskTemplates.value[0]?.id
    const belongsToCurrentStages = firstTaskId && stages.some((stage) => `task-${stage.code}` === firstTaskId)
    if (!taskTemplates.value.length || !belongsToCurrentStages) seedDesignSurfaces(stages)
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
    slaLabel: stage.slaRef ? '已绑定 SLA' : '未绑定 SLA',
    formCount: stage.formRefs?.length ?? 0,
    evidenceCount: stage.evidenceRefs?.length ?? 0,
    description: stage.description,
    terminal: stage.terminal,
    status: stage.terminal ? 'pending' : 'current',
  }
}

function seedDesignSurfaces(stages: WorkflowCanvasStage[]) {
  taskTemplates.value = stages.map((stage) => ({
    id: `task-${stage.code}`,
    name: `${stage.name}任务`,
    owner: stage.ownerLabel,
    sla: stage.slaLabel === '已绑定 SLA' ? '24 小时' : '待配置',
    form: stage.formCount ? '勘测表' : '未关联',
    evidence: stage.evidenceCount ? '现场照片' : '按节点配置',
    completion: `${stage.name}完成并通过完整性检查后，进入下一履约阶段。`,
    stage: `阶段 ${String(stage.sequence).padStart(2, '0')}`,
  }))
  formFields.value = [
    { id: 'customer-name', label: '客户姓名', type: 'text', required: true, placeholder: '请输入客户姓名' },
    { id: 'site-address', label: '安装地址', type: 'text', required: true, placeholder: '请输入完整服务地址' },
    { id: 'charger-power', label: '设备功率', type: 'number', required: true, placeholder: '请输入设备功率（kW）' },
    { id: 'survey-result', label: '勘测结论', type: 'select', required: true, placeholder: '请选择勘测结论', options: ['可安装', '需整改', '暂不可安装'] },
    { id: 'site-photo', label: '现场照片', type: 'image', required: true, placeholder: '上传现场照片' },
  ]
  slaRules.value = stages.slice(0, 5).map((stage) => ({
    id: `sla-${stage.code}`,
    task: stage.name,
    target: stage.sequence === 1 ? '4 小时' : '24 小时',
    warning: stage.sequence === 1 ? '3 小时' : '20 小时',
    escalation: stage.sequence === 1 ? '超时 8 小时升级项目经理' : '超时 4 小时通知责任网点',
    basis: stage.sequence === 1 ? '工单受理' : '上一阶段完成',
  }))
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
        <p class="breadcrumb">客户与项目 / {{ project.data.value?.projectName ?? '项目' }} / 履约方案</p>
        <h1>履约方案设计器 <span>Blueprint Designer</span></h1>
        <p>把流程、任务、表单、证据、SLA 和版本放在同一个可解释的履约蓝图里。</p>
      </div>
      <div class="heading-actions">
        <RouterLink class="secondary-link-button" :to="`/projects/${projectId}`">返回项目驾驶舱</RouterLink>
        <Button @click="openSimulation">模拟方案匹配</Button>
        <Button type="primary" @click="createOpen = true">新建履约方案</Button>
      </div>
    </header>

    <div v-if="createdProfileName" class="product-notice success">已创建履约方案“{{ createdProfileName }}”，请继续完成蓝图设计和发布校验。</div>
    <PageError v-if="profiles.isError.value" :detail="profiles.error.value?.message ?? '履约方案加载失败'" />
    <div v-else-if="profiles.isLoading.value" class="page-loading">正在加载履约蓝图…</div>
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
          <VersionBadge :status="selectedProfile.data.value?.status" :version="selectedProfile.data.value?.activeVersion" />
          <RouterLink v-if="selectedProfile.data.value?.draftRevisionId" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">编辑活动草稿</RouterLink>
          <RouterLink v-if="selectedProfile.data.value" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/publish`">版本与发布</RouterLink>
        </template>
      </BlueprintSidebar>

      <main class="blueprint-canvas-column">
        <PageError v-if="selectedProfile.isError.value" :detail="selectedProfile.error.value?.message ?? '方案详情加载失败'" />
        <div v-else-if="selectedProfile.isLoading.value" class="page-loading">正在读取方案属性…</div>
        <template v-else-if="selectedProfile.data.value && selectedSummary">
          <header class="blueprint-main-heading">
            <div><span class="sos-eyebrow">{{ presentServiceProduct(selectedSummary.serviceProductCode) }}</span><h2>{{ selectedSummary.profileName }}</h2><p>{{ selectedProfile.data.value.description || '该方案用于定义一个可匹配的新能源服务场景。' }}</p></div>
            <div class="blueprint-main-heading__meta"><VersionBadge :status="selectedProfile.data.value.status" :version="selectedProfile.data.value.activeVersion" /><span>优先级 {{ selectedSummary.matchPriority }}</span></div>
          </header>

          <section class="blueprint-home-metrics" aria-label="履约方案运行摘要">
            <ProjectMetricCard label="运行工单" :value="runOrderCount" hint="当前项目范围" tone="blue" />
            <ProjectMetricCard label="成功率" :value="fulfillmentSuccessRate.value" :hint="fulfillmentSuccessRate.hint" tone="success" />
            <ProjectMetricCard label="当前版本" :value="selectedProfile.data.value.activeVersion ? `V${selectedProfile.data.value.activeVersion}` : '尚未发布'" hint="生效版本不可原地编辑" tone="success" />
            <ProjectMetricCard label="最近修改" :value="lastModifiedLabel" hint="方案资料更新时间" />
          </section>

          <template v-if="activeSection === 'flow'">
            <div v-if="draft.isLoading.value" class="blueprint-loading-line">正在加载流程资产…</div>
            <WorkflowDesigner :stages="canvasStages" :selected-code="selectedStageCode" :readonly="!isDraftEditable" @select="selectedStageCode = $event.code" />
            <section class="blueprint-flow-note"><div><strong>流程是履约版本的骨架</strong><p>节点、责任、任务、表单、资料和 SLA 通过同一方案版本整体校验和发布；编辑请进入活动草稿。</p></div><RouterLink v-if="selectedProfile.data.value.draftRevisionId" class="primary-link-button" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/draft`">打开草稿设计</RouterLink></section>
          </template>
          <section v-else-if="activeSection === 'base'" class="blueprint-summary-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">SERVICE CONTEXT</span><h3>基础信息与适用范围</h3><p>方案以结构化条件参与正式受理匹配。</p></div><Tag color="processing">{{ selectedSummary.matchPriority }} 优先级</Tag></header>
            <dl class="blueprint-fact-grid"><div><dt>方案编码</dt><dd>{{ selectedProfile.data.value.profileCode }}</dd></div><div><dt>服务产品</dt><dd>{{ presentServiceProduct(selectedSummary.serviceProductCode) }}</dd></div><div><dt>活动草稿</dt><dd>{{ selectedProfile.data.value.draftRevisionId ? '存在未发布草稿' : '无活动草稿' }}</dd></div><div><dt>最近更新</dt><dd>{{ selectedProfile.data.value.updatedAt.slice(0, 16).replace('T', ' ') }}</dd></div></dl>
            <div class="blueprint-unavailable-note">详细品牌、省域和其他结构化匹配条件在草稿设计中维护；当前接口未返回时不在页面猜测适用范围。</div>
          </section>
          <TaskTemplatePanel
            v-else-if="activeSection === 'tasks'"
            :tasks="taskTemplates"
            :editable="isDraftEditable"
            @update:tasks="taskTemplates = $event"
          />
          <FormDesigner
            v-else-if="activeSection === 'forms'"
            :fields="formFields"
            :editable="isDraftEditable"
            @update:fields="formFields = $event"
          />
          <SlaPanel
            v-else-if="activeSection === 'sla'"
            :rules="slaRules"
            :editable="isDraftEditable"
            @update:rules="slaRules = $event"
          />
          <section v-else-if="activeSection === 'evidence'" class="blueprint-summary-surface blueprint-evidence-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">EVIDENCE RULES</span><h3>证据要求设计</h3><p>资料槽位与审核依据随方案版本冻结，当前展示来自已绑定履约阶段的摘要。</p></div><Tag color="processing">{{ selectedSummary.evidenceCount }} 项资料</Tag></header>
            <div class="blueprint-evidence-grid">
              <article><span>现场照片</span><strong>安装前后照片</strong><small>现场勘测、安装和验收阶段使用</small><Tag color="success">必需</Tag></article>
              <article><span>客户确认</span><strong>客户签字或电子确认</strong><small>验收完成前必须形成资料快照</small><Tag color="success">必需</Tag></article>
              <article><span>整改凭证</span><strong>整改前后对照</strong><small>存在质量异常时进入补充资料槽位</small><Tag>按需</Tag></article>
            </div>
            <div class="blueprint-unavailable-note">证据规则不能脱离审核与整改单独发布；修改请进入活动草稿并与流程、表单、SLA 一起校验。</div>
          </section>
          <section v-else-if="activeSection === 'versions'" class="blueprint-summary-surface blueprint-version-surface">
            <header class="sos-panel-heading"><div><span class="sos-eyebrow">VERSION MANAGEMENT</span><h3>版本管理</h3><p>以版本时间线理解当前生效规则、历史变更和下一次发布边界。</p></div><RouterLink v-if="selectedProfile.data.value.draftRevisionId" class="primary-link-button" :to="`/projects/${projectId}/fulfillment/${selectedProfileId}/publish`">进入发布检查</RouterLink></header>
            <div class="blueprint-version-summary"><ProjectMetricCard label="当前版本" :value="selectedProfile.data.value.activeVersion ? `V${selectedProfile.data.value.activeVersion}` : '尚未发布'" hint="生效版本不可原地编辑" tone="success" /><ProjectMetricCard label="活动草稿" :value="selectedProfile.data.value.draftRevisionId ? '有待发布变更' : '无活动草稿'" hint="草稿发布后生成新版本" tone="warning" /><ProjectMetricCard label="版本数量" :value="String(versionItems.length)" hint="含历史与当前版本" /></div>
            <VersionTimeline :items="versionItems" :editable="Boolean(selectedProfile.data.value.draftRevisionId)" @view="selectedRevision = $event" @copy="openRevisionCopy" />
            <div v-if="selectedRevision" class="blueprint-revision-note"><strong>V{{ selectedRevision.version }} · {{ selectedRevision.statusLabel }}</strong><span>{{ selectedRevision.summary }}</span></div>
          </section>
        </template>
        <div v-else class="blueprint-main-empty"><strong>请选择一套履约方案</strong><span>方案蓝图会在这里展示流程、责任、资料和版本关系。</span></div>
      </main>

      <aside class="blueprint-inspector">
        <header class="blueprint-inspector__heading"><div><span class="sos-eyebrow">NODE PROPERTIES</span><h2>{{ selectedStage?.name ?? '方案属性' }}</h2></div><span v-if="selectedStage" class="blueprint-inspector__node-number">{{ String(selectedStage.sequence).padStart(2, '0') }}</span></header>
        <template v-if="selectedStage">
          <p class="blueprint-inspector__description">{{ selectedStage.description || '该节点暂无额外业务说明。' }}</p>
          <dl class="blueprint-inspector__facts"><div><dt>节点类型</dt><dd>{{ selectedStage.typeLabel }}</dd></div><div><dt>负责人</dt><dd>{{ selectedStage.ownerLabel }}</dd></div><div><dt>SLA</dt><dd>{{ selectedStage.slaLabel }}</dd></div><div><dt>关联任务</dt><dd>{{ selectedStage.taskLabel }}</dd></div><div><dt>关联表单</dt><dd>{{ selectedStage.formCount }} 份</dd></div><div><dt>证据要求</dt><dd>{{ selectedStage.evidenceCount }} 项</dd></div></dl>
          <div class="blueprint-inspector__hint"><strong>节点随版本冻结</strong><span>修改需要进入活动草稿，并重新执行完整校验。</span></div>
        </template>
        <template v-else>
          <p class="blueprint-inspector__description">选择中间画布的履约节点，查看负责人、SLA、任务、表单和证据要求。</p>
          <div class="blueprint-inspector__overview"><span>当前方案规模</span><strong>{{ selectedSummary?.stageCount ?? 0 }} 个阶段</strong><span>{{ selectedSummary?.formCount ?? 0 }} 份表单 · {{ selectedSummary?.evidenceCount ?? 0 }} 项资料</span></div>
        </template>
      </aside>
    </section>

    <Drawer :open="createOpen" width="520" title="新建履约方案" @close="closeCreate">
      <p class="create-user-intro">履约方案用于定义一种服务产品的完整履约规则。创建后仍需完成蓝图设计、校验和发布，才会用于新工单。</p>
      <PageError v-if="createCommand.isError.value" :detail="createCommand.error.value?.message ?? '履约方案创建失败'" />
      <Form layout="vertical">
        <Form.Item label="方案名称" required><Input v-model:value="form.profileName" placeholder="例如：家充勘测与安装" :maxlength="200" /></Form.Item>
        <Form.Item label="服务产品编码" required><Input v-model:value="form.serviceProductCode" placeholder="例如：HOME_CHARGING_INSTALL" :maxlength="96" /></Form.Item>
        <Form.Item label="方案编码" required><Input v-model:value="form.profileCode" placeholder="例如：BYD_SHANDONG_HOME_STANDARD" :maxlength="96" /><small class="field-hint">方案编码在项目内唯一；同一服务产品可以配置多套不同适用范围的方案。</small></Form.Item>
        <Form.Item label="匹配优先级" required><Input v-model:value="form.matchPriority" type="number" :min="-10000" :max="10000" /><small class="field-hint">数字越大越优先；同优先级时选择适用条件更具体的方案。</small></Form.Item>
        <Form.Item label="初始化方式" required><Select v-model:value="initializationMode" :options="initializationOptions" /><small class="field-hint">复制现有方案会在同一项目范围内原子复制流程文档、Workflow 与配置包运行绑定。</small></Form.Item>
        <Form.Item label="方案说明"><Input.TextArea v-model:value="form.description" :rows="4" placeholder="说明适用业务范围和履约目标" /></Form.Item>
      </Form>
      <template #footer><div class="drawer-footer"><Button @click="closeCreate">取消</Button><Button type="primary" :disabled="!form.profileName.trim() || !form.profileCode?.trim() || !form.serviceProductCode.trim()" :loading="createCommand.isPending.value" @click="createProfile">创建方案</Button></div></template>
    </Drawer>

    <Drawer :open="simulationOpen" width="560" title="履约方案匹配模拟器" @close="closeSimulation">
      <p class="create-user-intro">使用与正式建单相同的适用范围、匹配优先级和规则具体度算法；模拟不会创建工单或运行实例。</p>
      <PageError v-if="simulationError" :detail="simulationError" />
      <Form layout="vertical"><Form.Item label="服务产品编码" required><Input v-model:value="simulationForm.serviceProductCode" :maxlength="96" /></Form.Item><Form.Item label="客户品牌编码"><Input v-model:value="simulationForm.brandCode" placeholder="例如：BYD_OCEAN" :maxlength="64" /></Form.Item><Form.Item label="省级行政区编码"><Input v-model:value="simulationForm.provinceCode" placeholder="例如：370000" :maxlength="6" /></Form.Item></Form>
      <section v-if="simulationResult" class="fulfillment-simulation-result"><header><div><span>唯一命中</span><h3>{{ simulationResult.profileName }}</h3></div><Tag color="success">V{{ simulationResult.fulfillmentVersion }}</Tag></header><dl><div><dt>方案编码</dt><dd>{{ simulationResult.profileCode }}</dd></div><div><dt>匹配优先级</dt><dd>{{ simulationResult.matchPriority }}</dd></div><div><dt>规则具体度</dt><dd>{{ simulationResult.matchSpecificity }}</dd></div><div><dt>配置包版本</dt><dd>{{ simulationResult.configurationBundleVersion }}</dd></div></dl><div class="fulfillment-simulation-reasons"><strong>命中解释</strong><Tag v-for="reason in simulationResult.matchExplanation" :key="reason" color="processing">{{ reason }}</Tag></div></section>
      <template #footer><div class="drawer-footer"><Button @click="closeSimulation">关闭</Button><Button type="primary" :disabled="!simulationForm.serviceProductCode.trim()" :loading="simulationLoading" @click="runSimulation">开始模拟</Button></div></template>
    </Drawer>
  </div>
</template>
