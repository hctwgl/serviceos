<script setup lang="ts">
/**
 * Admin 工作流设计器产品页（M386）。
 * 仅承载 WORKFLOW ConfigurationDraft；画布内部仍用 definitionJson 适配，
 * 产品主界面不展示 JSON（UI_DATA_GAP：结构化 Workflow Draft DTO 待后续）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  Select,
  Space,
  Tag,
} from 'ant-design-vue'
import { ArrowLeftOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import PageHeader from '../patterns/PageHeader.vue'
import RightContextRail from '../patterns/RightContextRail.vue'
import WorkflowCanvas, { type WorkflowNode } from '../components/WorkflowCanvas.vue'
import {
  approveConfigurationDraft,
  createConfigurationDraft,
  listConfigurationDrafts,
  publishConfigurationDraft,
  updateConfigurationDraft,
  validateConfigurationDraft,
  type ConfigurationDraft,
} from '../api/configurationDrafts'
import { toUserFacingError } from '../product/errorMessages'
import { statusLabel } from '../product/statusLabels'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'

const router = useRouter()
const diagnostics = useDeveloperDiagnostics()

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const drafts = ref<ConfigurationDraft[]>([])
const selected = ref<ConfigurationDraft | null>(null)
const definitionText = ref('')
const selectedNode = ref<WorkflowNode | null>(null)
const createName = ref('家充勘测安装流程')
const createKey = ref('platform.workflow.home-charging')
const createVersion = ref('1.0.0')
const approvalRef = ref('APR-WF-LOCAL-1')

const NODE_TYPE_OPTIONS = [
  { value: 'START', label: '开始' },
  { value: 'USER_TASK', label: '人工任务' },
  { value: 'SERVICE_TASK', label: '系统任务' },
  { value: 'REVIEW_TASK', label: '审核任务' },
  { value: 'EXCLUSIVE_GATEWAY', label: '条件网关' },
  { value: 'WAIT_EVENT', label: '等待事件' },
  { value: 'END', label: '结束' },
]

const draftStatusTone = computed(() => {
  const status = selected.value?.status
  if (status === 'PUBLISHED' || status === 'APPROVED') return 'success'
  if (status === 'VALIDATED') return 'info'
  if (status === 'DISCARDED') return 'critical'
  return 'warning'
})

function nodeTypeLabel(type: string | undefined): string {
  return NODE_TYPE_OPTIONS.find((item) => item.value === type)?.label || type || '未知类型'
}

function friendlyDraftTitle(draft: ConfigurationDraft): string {
  const key = draft.assetKey
  if (key.includes('home-charging') || key.includes('survey')) return '家充勘测安装流程'
  if (key.includes('repair')) return '维修流程'
  if (key.includes('relocate') || key.includes('move')) return '移机流程'
  return key
}

function defaultWorkflowDefinition(): string {
  return JSON.stringify(
    {
      workflowKey: createKey.value,
      semanticVersion: createVersion.value,
      startNodeId: 'START',
      nodes: [
        { nodeId: 'START', nodeType: 'START', name: '开始' },
        {
          nodeId: 'INTAKE',
          nodeType: 'USER_TASK',
          name: '受理协调',
          stageCode: 'INTAKE',
          taskType: 'DISPATCH',
        },
        {
          nodeId: 'SURVEY',
          nodeType: 'USER_TASK',
          name: '上门勘测',
          stageCode: 'SURVEY',
          taskType: 'SURVEY',
        },
        {
          nodeId: 'INSTALL',
          nodeType: 'USER_TASK',
          name: '上门安装',
          stageCode: 'INSTALLATION',
          taskType: 'INSTALL',
        },
        { nodeId: 'END', nodeType: 'END', name: '完成' },
      ],
      transitions: [
        { transitionId: 't1', from: 'START', to: 'INTAKE' },
        { transitionId: 't2', from: 'INTAKE', to: 'SURVEY' },
        { transitionId: 't3', from: 'SURVEY', to: 'INSTALL' },
        { transitionId: 't4', from: 'INSTALL', to: 'END' },
      ],
      metadata: {
        layout: {
          START: { x: 40, y: 40 },
          INTAKE: { x: 240, y: 40 },
          SURVEY: { x: 440, y: 40 },
          INSTALL: { x: 640, y: 40 },
          END: { x: 840, y: 40 },
        },
        displayName: createName.value,
      },
    },
    null,
    2,
  )
}

async function refreshList(preferredDraftId?: string) {
  loading.value = true
  error.value = null
  try {
    drafts.value = await listConfigurationDrafts('WORKFLOW')
    const next =
      drafts.value.find((item) => item.draftId === preferredDraftId) ||
      drafts.value.find((item) => item.draftId === selected.value?.draftId) ||
      drafts.value[0] ||
      null
    if (next) {
      selectDraft(next)
    } else {
      selected.value = null
      definitionText.value = defaultWorkflowDefinition()
      selectedNode.value = null
    }
  } catch (err) {
    error.value = toUserFacingError(err).message
    drafts.value = []
  } finally {
    loading.value = false
  }
}

function selectDraft(draft: ConfigurationDraft) {
  selected.value = draft
  definitionText.value = draft.definitionJson
  selectedNode.value = null
  message.value = null
  if (draft.definitionJson) {
    diagnostics.pushDiagnostic({
      title: '工作流定义 JSON（仅诊断）',
      fields: {
        draftId: draft.draftId,
        assetKey: draft.assetKey,
        definitionJson: draft.definitionJson.slice(0, 4000),
      },
    })
  }
}

async function createDraft() {
  busy.value = true
  error.value = null
  message.value = null
  try {
    const created = await createConfigurationDraft({
      assetType: 'WORKFLOW',
      assetKey: createKey.value.trim(),
      intendedSemanticVersion: createVersion.value.trim(),
      schemaVersion: '1.0.0',
      definitionJson: defaultWorkflowDefinition(),
    })
    message.value = '已创建工作流草稿'
    await refreshList(created.data.draftId)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    busy.value = false
  }
}

async function saveDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const updated = await updateConfigurationDraft(
      selected.value.draftId,
      definitionText.value,
      selected.value.aggregateVersion,
    )
    message.value = '草稿已保存'
    await refreshList(updated.data.draftId)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    busy.value = false
  }
}

async function validateDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    await saveDraft()
    const validated = await validateConfigurationDraft(selected.value.draftId)
    selected.value = validated.data
    message.value = validated.data.validationErrors?.length
      ? `校验完成：${validated.data.validationErrors.length} 个问题`
      : '校验通过'
    await refreshList(validated.data.draftId)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    busy.value = false
  }
}

async function approveDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  try {
    const approved = await approveConfigurationDraft(
      selected.value.draftId,
      approvalRef.value.trim() || 'APR-WF-LOCAL-1',
      selected.value.aggregateVersion,
    )
    message.value = '已审批，可发布'
    await refreshList(approved.data.draftId)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    busy.value = false
  }
}

async function publishDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  try {
    const published = await publishConfigurationDraft(selected.value.draftId)
    message.value = '工作流已发布'
    await refreshList(published.data.draftId)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    busy.value = false
  }
}

function updateSelectedNodeField<K extends keyof WorkflowNode>(field: K, value: WorkflowNode[K]) {
  if (!selectedNode.value || !definitionText.value) return
  try {
    const raw = JSON.parse(definitionText.value) as {
      nodes?: WorkflowNode[]
      [key: string]: unknown
    }
    const nodes = Array.isArray(raw.nodes) ? [...raw.nodes] : []
    const index = nodes.findIndex((node) => node.nodeId === selectedNode.value?.nodeId)
    if (index < 0) return
    nodes[index] = { ...nodes[index], [field]: value }
    definitionText.value = JSON.stringify({ ...raw, nodes }, null, 2)
    selectedNode.value = nodes[index]
  } catch {
    error.value = '无法更新节点：流程定义结构无效'
  }
}

watch(
  () => selected.value?.status,
  () => {
    message.value = message.value
  },
)

onMounted(() => refreshList())
</script>

<template>
  <div class="workflow-designer" data-testid="workflow-designer-page">
    <PageHeader
      title="工作流设计器"
      description="以可视化方式定义履约路径、任务节点、分支与结束条件。普通模式不直接编辑 JSON。"
    >
      <template #breadcrumb>
        <a @click.prevent="router.push({ name: 'ADMIN.CONFIGURATION.DESIGNER' })">配置中心</a>
        <span> / 工作流设计器</span>
      </template>
      <template #meta>
        <Tag
          v-if="selected"
          :color="
            draftStatusTone === 'success'
              ? 'success'
              : draftStatusTone === 'critical'
                ? 'error'
                : 'processing'
          "
        >
          {{ statusLabel(selected.status) }}
        </Tag>
        <Tag v-if="selected">v{{ selected.intendedSemanticVersion }}</Tag>
      </template>
      <template #secondary-actions>
        <Button @click="router.push({ name: 'ADMIN.CONFIGURATION.DESIGNER' })">
          <template #icon><ArrowLeftOutlined /></template>
          全部配置资产
        </Button>
      </template>
      <template #primary-action>
        <Space wrap>
          <Button :loading="busy" :disabled="!selected" @click="saveDraft">
            <template #icon><SaveOutlined /></template>
            保存草稿
          </Button>
          <Button :loading="busy" :disabled="!selected" @click="validateDraft">校验</Button>
          <Button
            :loading="busy"
            :disabled="!selected || selected.status !== 'VALIDATED'"
            @click="approveDraft"
          >
            审批
          </Button>
          <Button
            type="primary"
            :loading="busy"
            :disabled="!selected || selected.status !== 'APPROVED'"
            @click="publishDraft"
          >
            发布
          </Button>
        </Space>
      </template>
    </PageHeader>

    <Alert
      v-if="error"
      type="error"
      show-icon
      :message="error"
      style="margin-bottom: 12px"
    />
    <Alert
      v-if="message"
      type="success"
      show-icon
      :message="message"
      style="margin-bottom: 12px"
    />
    <Alert
      type="info"
      show-icon
      message="UI_DATA_GAP：工作流结构化 Draft DTO 尚未独立发布"
      description="当前通过服务端 ConfigurationDraft.definitionJson 适配画布；产品页不展示 JSON，正式结构化契约将在后续切片补齐。"
      style="margin-bottom: 12px"
    />

    <div class="workflow-designer__body">
      <aside class="workflow-designer__left" aria-label="流程目录与节点组件">
        <section class="panel">
          <div class="panel__head">
            <h2>流程目录</h2>
            <Button size="small" type="link" :loading="busy" @click="createDraft">
              <template #icon><PlusOutlined /></template>
              新建
            </Button>
          </div>
          <div class="create-form">
            <Input v-model:value="createName" placeholder="流程显示名" aria-label="流程显示名" />
            <Input v-model:value="createKey" placeholder="流程编码" aria-label="流程编码" />
            <Input v-model:value="createVersion" placeholder="版本号" aria-label="版本号" />
          </div>
          <ul v-if="drafts.length" class="draft-list" data-testid="workflow-draft-list">
            <li
              v-for="draft in drafts"
              :key="draft.draftId"
              :class="{ active: selected?.draftId === draft.draftId }"
              @click="selectDraft(draft)"
            >
              <strong>{{ friendlyDraftTitle(draft) }}</strong>
              <span>{{ statusLabel(draft.status) }} · v{{ draft.intendedSemanticVersion }}</span>
            </li>
          </ul>
          <Empty v-else-if="!loading" description="还没有工作流草稿" />
        </section>
        <section class="panel">
          <h2>节点组件</h2>
          <p class="hint">在画布工具栏选择节点类型后添加；以下为产品化节点说明。</p>
          <ul class="palette">
            <li v-for="item in NODE_TYPE_OPTIONS" :key="item.value">{{ item.label }}</li>
          </ul>
        </section>
      </aside>

      <main class="workflow-designer__canvas" aria-label="流程画布">
        <WorkflowCanvas
          v-if="definitionText"
          :definition-json="definitionText"
          data-testid="workflow-product-canvas"
          @update:definition-json="definitionText = $event"
          @select-node="selectedNode = $event"
        />
        <Empty v-else description="请选择或新建工作流草稿" />
        <section
          v-if="selected?.validationErrors?.length"
          class="validation"
          data-testid="workflow-validation"
        >
          <h3>校验结果</h3>
          <ul>
            <li v-for="(item, index) in selected.validationErrors" :key="index">{{ item }}</li>
          </ul>
        </section>
      </main>

      <RightContextRail title="节点配置">
        <template v-if="selectedNode">
          <Form layout="vertical">
            <FormItem label="节点名称">
              <Input
                :value="selectedNode.name || ''"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField('name', (e.target as HTMLInputElement).value)
                "
              />
            </FormItem>
            <FormItem label="节点类型">
              <Select
                :value="selectedNode.nodeType"
                style="width: 100%"
                :options="NODE_TYPE_OPTIONS"
                @change="(value) => updateSelectedNodeField('nodeType', String(value))"
              />
            </FormItem>
            <FormItem label="任务类型">
              <Input
                :value="selectedNode.taskType || ''"
                placeholder="如 SURVEY / INSTALL"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField(
                      'taskType',
                      (e.target as HTMLInputElement).value.trim() || null,
                    )
                "
              />
            </FormItem>
            <FormItem label="阶段编码">
              <Input
                :value="selectedNode.stageCode || ''"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField(
                      'stageCode',
                      (e.target as HTMLInputElement).value.trim() || null,
                    )
                "
              />
            </FormItem>
            <FormItem label="表单引用">
              <Input
                :value="selectedNode.formRef || ''"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField(
                      'formRef',
                      (e.target as HTMLInputElement).value.trim() || null,
                    )
                "
              />
            </FormItem>
            <FormItem label="资料引用">
              <Input
                :value="selectedNode.evidenceRef || ''"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField(
                      'evidenceRef',
                      (e.target as HTMLInputElement).value.trim() || null,
                    )
                "
              />
            </FormItem>
            <FormItem label="SLA 引用">
              <Input
                :value="selectedNode.slaRef || ''"
                @change="
                  (e: Event) =>
                    updateSelectedNodeField(
                      'slaRef',
                      (e.target as HTMLInputElement).value.trim() || null,
                    )
                "
              />
            </FormItem>
          </Form>
          <p class="hint">当前类型：{{ nodeTypeLabel(String(selectedNode.nodeType)) }}</p>
        </template>
        <Empty v-else description="在画布中选中节点以编辑属性" />
        <FormItem v-if="selected" label="审批编号" style="margin-top: 16px">
          <Input v-model:value="approvalRef" aria-label="审批编号" />
        </FormItem>
      </RightContextRail>
    </div>
  </div>
</template>

<style scoped>
.workflow-designer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
}
.workflow-designer__body {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}
.workflow-designer__left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel {
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  padding: 12px;
}
.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel h2,
.validation h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
.create-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.draft-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.draft-list li {
  padding: 8px 10px;
  border-radius: var(--sos-radius-md);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.draft-list li.active,
.draft-list li:hover {
  background: var(--sos-primary-100);
}
.draft-list span,
.hint {
  font-size: 12px;
  color: var(--sos-color-text-tertiary);
}
.palette {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-text-secondary);
}
.workflow-designer__canvas {
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  padding: 12px;
  min-height: 560px;
}
.validation {
  margin-top: 12px;
  border-top: 1px solid var(--sos-color-border-default);
  padding-top: 12px;
}
.validation ul {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-status-critical-fg);
}
@media (max-width: 1280px) {
  .workflow-designer__body {
    grid-template-columns: 220px minmax(0, 1fr);
  }
  .workflow-designer__body :deep(.sos-right-rail) {
    grid-column: 1 / -1;
  }
}
</style>
