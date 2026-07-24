<script setup lang="ts">
import type { ProjectFulfillmentDocument } from '@serviceos/api-client'

import {
  Button,
  Drawer,
  Input,
  Modal,
  Tag,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import WorkflowDesignerWorkspace from '../components/WorkflowDesignerWorkspace.vue'
import {
  useCompileProjectFulfillmentPreviewCommand,
  useUpdateProjectFulfillmentDraftCommand,
  useValidateProjectFulfillmentDraftCommand,
} from '../commands/use-project-fulfillment-draft-commands'
import { useProjectFulfillmentDraftQuery } from '../queries/use-project-fulfillment-query'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))
const draft = useProjectFulfillmentDraftQuery(projectId, profileId)
const updateCommand = useUpdateProjectFulfillmentDraftCommand(
  () => projectId.value,
  () => profileId.value,
)
const validateCommand = useValidateProjectFulfillmentDraftCommand(
  () => projectId.value,
  () => profileId.value,
)
const previewCommand = useCompileProjectFulfillmentPreviewCommand(
  () => projectId.value,
  () => profileId.value,
)
const model = reactive<{
  description: string
  document: ProjectFulfillmentDocument
  profileName: string
}>({
  description: '',
  document: {
    matchRule: { brandCodes: [], provinceCodes: [] },
    nodes: [],
    orderTypeName: null,
    phases: [],
    schemaVersion: '2.0.0',
    stages: [],
    transitions: [],
  },
  profileName: '',
})
const savedAt = ref<string>()
const validationOpen = ref(false)
const simulationOpen = ref(false)
const scopeOpen = ref(false)
const dirty = ref(false)

const completionPercent = computed(() => {
  const checks = [
    model.document.nodes.filter((node) => node.nodeType === 'START').length === 1,
    model.document.nodes.some((node) => node.nodeType === 'END'),
    model.document.phases.length > 0,
    model.document.transitions.length >= Math.max(0, model.document.nodes.length - 1),
    model.document.nodes.filter((node) => ['HUMAN_TASK', 'REVIEW'].includes(node.nodeType))
      .every((node) => Object.keys(node.task).length && node.responsibilityRole),
    model.document.nodes.filter((node) => node.nodeType === 'SYSTEM_ACTION')
      .every((node) => Object.keys(node.systemAction).length),
    model.document.nodes.filter((node) => node.nodeType === 'EVENT_WAIT')
      .every((node) => Object.keys(node.eventWait).length),
    Boolean(previewCommand.data.value || draft.data.value?.simulatedAt),
  ]
  return Math.round(checks.filter(Boolean).length / checks.length * 100)
})

function normalizeDocument(document: ProjectFulfillmentDocument): ProjectFulfillmentDocument {
  // Vue Query 返回值可能已被 Vue 包装为只读 Proxy，structuredClone 会抛 DataCloneError。
  // 履约文档是纯 JSON 契约，用 JSON 往返可安全获得设计器自己的可编辑副本。
  const snapshot = JSON.parse(JSON.stringify(document)) as ProjectFulfillmentDocument
  return {
    ...snapshot,
    nodes: snapshot.nodes ?? [],
    phases: snapshot.phases ?? [],
    schemaVersion: document.nodes?.length ? document.schemaVersion : '2.0.0',
    stages: snapshot.stages ?? [],
    transitions: snapshot.transitions ?? [],
  }
}

function parseCodes(value: unknown) {
  return String(value ?? '')
    .split(/[,，\s]+/)
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
    .filter((item, index, values) => values.indexOf(item) === index)
}

watch(
  () => draft.data.value,
  (value) => {
    if (!value) return
    model.profileName = value.profileName
    model.description = value.description ?? ''
    model.document = normalizeDocument(value.document)
    dirty.value = false
  },
  { immediate: true },
)

function updateDocument(value: ProjectFulfillmentDocument) {
  model.document = value
  dirty.value = true
  validateCommand.reset()
  previewCommand.reset()
}

async function saveDraft() {
  if (!draft.data.value) return
  await updateCommand.mutateAsync({
    aggregateVersion: draft.data.value.aggregateVersion,
    description: model.description.trim() || undefined,
    document: model.document,
    profileName: model.profileName.trim(),
    sourceBundleId: draft.data.value.sourceBundleId,
    workflowAssetVersionId: draft.data.value.workflowAssetVersionId,
  })
  dirty.value = false
  savedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  validateCommand.reset()
  previewCommand.reset()
}

async function validateDraft() {
  if (dirty.value) await saveDraft()
  await validateCommand.mutateAsync()
  validationOpen.value = true
}

async function simulateDraft() {
  if (dirty.value) await saveDraft()
  const issues = await validateCommand.mutateAsync()
  if (issues.some((issue) => issue.severity === 'ERROR')) {
    validationOpen.value = true
    return
  }
  await previewCommand.mutateAsync()
  simulationOpen.value = true
}

function updateScope(
  dimension: 'brandCodes' | 'provinceCodes',
  value: unknown,
) {
  const current = model.document.matchRule ?? { brandCodes: [], provinceCodes: [] }
  model.document.matchRule = { ...current, [dimension]: parseCodes(value) }
  dirty.value = true
}

function focusIssue(issue: { nodeId?: string | null }) {
  validationOpen.value = false
  if (issue.nodeId) {
    document.querySelector(`[data-id="${globalThis.CSS.escape(issue.nodeId)}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}
</script>

<template>
  <Page content-class="workflow-designer-page">
    <PageError v-if="draft.isError.value" :detail="draft.error.value?.message ?? '履约草稿加载失败'" />
    <div v-else-if="draft.isLoading.value" class="page-loading">正在加载履约流程设计器…</div>
    <template v-else-if="draft.data.value">
      <header class="workflow-version-bar">
        <div class="workflow-version-identity">
          <RouterLink :to="`/projects/${projectId}/fulfillment?profileId=${profileId}`" aria-label="返回履约方案">←</RouterLink>
          <strong>ServiceOS</strong><i>/</i><span>履约蓝图设计器</span><i>/</i>
          <Input v-model:value="model.profileName" :bordered="false" :maxlength="200" @change="dirty = true" />
          <Tag :color="draft.data.value.simulatedAt ? 'orange' : 'blue'">
            V{{ draft.data.value.aggregateVersion }}（{{ draft.data.value.simulatedAt ? '测试中' : '草稿' }}）
          </Tag>
        </div>
        <div class="workflow-version-actions">
          <button class="scope-summary" type="button" @click="scopeOpen = true">
            <span>适用范围</span>
            <strong>{{ model.document.matchRule?.brandCodes.join('、') || '未限制品牌' }} · {{ model.document.matchRule?.provinceCodes.join('、') || '未限制区域' }}</strong>
          </button>
          <span class="workflow-completion"><i :style="{ width: `${completionPercent}%` }" /><b>{{ completionPercent }}%</b></span>
          <span class="autosave-state">{{ dirty ? '有未保存修改' : savedAt ? `已保存 ${savedAt}` : '草稿已同步' }}</span>
          <Button :loading="validateCommand.isPending.value" @click="validateDraft">校验</Button>
          <Button :loading="previewCommand.isPending.value" @click="simulateDraft">模拟运行</Button>
          <Button :loading="updateCommand.isPending.value" @click="saveDraft">保存</Button>
          <RouterLink :to="`/projects/${projectId}/fulfillment/${profileId}/publish`"><Button type="primary" :disabled="dirty">发布方案</Button></RouterLink>
        </div>
      </header>

      <PageError v-if="updateCommand.isError.value" :detail="updateCommand.error.value?.message ?? '草稿保存失败'" />
      <PageError v-if="validateCommand.isError.value" :detail="validateCommand.error.value?.message ?? '草稿校验失败'" />
      <PageError v-if="previewCommand.isError.value" :detail="previewCommand.error.value?.message ?? '模拟运行失败'" />

      <WorkflowDesignerWorkspace
        :document="model.document"
        :issues="validateCommand.data.value"
        @update:document="updateDocument"
      />

      <Drawer :open="scopeOpen" width="480" title="方案适用范围" @close="scopeOpen = false">
        <div class="workflow-scope-form">
          <label><span>客户品牌编码</span><Input :value="model.document.matchRule?.brandCodes.join(', ')" placeholder="例如：BYD_DYNASTY" @update:value="updateScope('brandCodes', $event)" /></label>
          <label><span>省级行政区编码</span><Input :value="model.document.matchRule?.provinceCodes.join(', ')" placeholder="例如：370000" @update:value="updateScope('provinceCodes', $event)" /></label>
          <label><span>工单类型名称</span><Input :value="model.document.orderTypeName ?? ''" @update:value="model.document.orderTypeName = String($event); dirty = true" /></label>
          <label><span>方案说明</span><Input.TextArea v-model:value="model.description" :rows="4" @change="dirty = true" /></label>
        </div>
      </Drawer>

      <Modal v-model:open="validationOpen" title="方案完整性校验" :footer="null" width="720">
        <div v-if="!validateCommand.data.value?.length" class="workflow-validation-success">
          <Tag color="success">通过</Tag><strong>流程结构和节点配置未发现阻塞问题</strong>
        </div>
        <div v-else class="workflow-validation-list">
          <button v-for="issue in validateCommand.data.value" :key="`${issue.errorCode}-${issue.nodeId}-${issue.transitionId}`" type="button" @click="focusIssue(issue)">
            <Tag :color="issue.severity === 'ERROR' ? 'error' : issue.severity === 'WARNING' ? 'warning' : 'processing'">{{ issue.severity === 'ERROR' ? '阻塞' : issue.severity === 'WARNING' ? '提醒' : '信息' }}</Tag>
            <span><strong>{{ issue.userMessage }}</strong><small>{{ issue.suggestion }}</small></span>
            <b>{{ issue.nodeId ? '定位节点 →' : '定位配置 →' }}</b>
          </button>
        </div>
      </Modal>

      <Modal v-model:open="simulationOpen" title="模拟运行结果" :footer="null" width="820">
        <div v-if="previewCommand.data.value" class="workflow-simulation-result">
          <header><Tag color="success">模拟通过</Tag><strong>{{ previewCommand.data.value.runbook.profileName }}</strong><span>{{ previewCommand.data.value.runbook.stageCount }} 个阶段</span></header>
          <ol>
            <li v-for="stage in previewCommand.data.value.runbook.stages" :key="`${stage.sequence}-${stage.stageName}`">
              <b>{{ stage.sequence }}</b><span><strong>{{ stage.stageName }}</strong><small>{{ stage.ownerTypeLabel }} · {{ stage.taskTypeLabel || '控制节点' }}</small></span><Tag v-if="stage.terminal" color="success">结束</Tag>
            </li>
          </ol>
          <p>{{ previewCommand.data.value.runbook.impactSummary }}</p>
        </div>
      </Modal>
    </template>
  </Page>
</template>
