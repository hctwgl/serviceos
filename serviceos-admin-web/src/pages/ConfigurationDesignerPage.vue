<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  analyzeConfigurationDraftDependencies,
  approveConfigurationDraft,
  createConfigurationDraft,
  diffConfigurationDraft,
  listConfigurationDrafts,
  publishConfigurationDraft,
  simulateConfigurationDraft,
  updateConfigurationDraft,
  validateConfigurationDraft,
  type ConfigurationDependencyReport,
  type ConfigurationDraft,
  type ConfigurationDraftDiff,
  type ConfigurationSimulationReport,
  type DesignerAssetType,
} from '../api/configurationDrafts'
import WorkflowCanvas from '../components/WorkflowCanvas.vue'

const assetType = ref<DesignerAssetType>('WORKFLOW')
const drafts = ref<ConfigurationDraft[]>([])
const selected = ref<ConfigurationDraft | null>(null)
const definitionText = ref('')
const diffView = ref<ConfigurationDraftDiff | null>(null)
const dependencyReport = ref<ConfigurationDependencyReport | null>(null)
const simulationReport = ref<ConfigurationSimulationReport | null>(null)
const simClientCode = ref('OEM_A')
const approvalRef = ref('APR-LOCAL-1')
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

const createKey = ref('platform.designer.demo')
const createVersion = ref('1.0.0')

const showCanvas = computed(() => assetType.value === 'WORKFLOW')

async function refreshList() {
  loading.value = true
  error.value = null
  try {
    drafts.value = await listConfigurationDrafts(assetType.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载草稿失败'
  } finally {
    loading.value = false
  }
}

function selectDraft(draft: ConfigurationDraft) {
  selected.value = draft
  definitionText.value = prettyJson(draft.definitionJson)
  diffView.value = null
  dependencyReport.value = null
  simulationReport.value = null
  message.value = null
  error.value = null
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function defaultDefinition(type: DesignerAssetType): string {
  if (type === 'WORKFLOW') {
    return JSON.stringify(
      {
        workflowKey: 'platform.designer.demo',
        semanticVersion: '1.0.0',
        startNodeId: 'START',
        nodes: [
          { nodeId: 'START', nodeType: 'START', name: '开始' },
          {
            nodeId: 'TASK_A',
            nodeType: 'SERVICE_TASK',
            name: '示例任务',
            stageCode: 'STAGE_A',
            taskType: 'DESIGNER_DEMO',
          },
          { nodeId: 'GW_BRANCH', nodeType: 'EXCLUSIVE_GATEWAY', name: '分支' },
          {
            nodeId: 'TASK_B',
            nodeType: 'SERVICE_TASK',
            name: '分支任务',
            stageCode: 'STAGE_B',
            taskType: 'DESIGNER_BRANCH',
          },
          { nodeId: 'END', nodeType: 'END', name: '结束' },
        ],
        transitions: [
          { transitionId: 't1', from: 'START', to: 'TASK_A' },
          { transitionId: 't2', from: 'TASK_A', to: 'GW_BRANCH' },
          {
            transitionId: 't3',
            from: 'GW_BRANCH',
            to: 'TASK_B',
            priority: 10,
            condition: {
              language: 'SERVICEOS_EXPR_V1',
              source: 'workOrder.serviceProductCode == "HOME_CHARGING_SURVEY_INSTALL"',
            },
          },
          {
            transitionId: 't4',
            from: 'GW_BRANCH',
            to: 'END',
            priority: 20,
            condition: {
              language: 'SERVICEOS_EXPR_V1',
              source: 'workOrder.serviceProductCode != "HOME_CHARGING_SURVEY_INSTALL"',
            },
          },
          { transitionId: 't5', from: 'TASK_B', to: 'END' },
        ],
        metadata: {
          layout: {
            START: { x: 40, y: 40 },
            TASK_A: { x: 40, y: 140 },
            GW_BRANCH: { x: 40, y: 240 },
            TASK_B: { x: 240, y: 340 },
            END: { x: 40, y: 340 },
          },
        },
      },
      null,
      2,
    )
  }
  if (type === 'SLA') {
    return JSON.stringify(
      {
        policyKey: 'platform.designer.demo.sla',
        version: '1.0.0',
        subjectType: 'TASK',
        taskTypes: ['DESIGNER_DEMO'],
        startEvent: 'TASK_CREATED',
        stopEvent: 'TASK_COMPLETED',
        clockMode: 'ELAPSED',
        targetDurationSeconds: 3600,
      },
      null,
      2,
    )
  }
  if (type === 'FORM') {
    return JSON.stringify(
      {
        formKey: 'platform.designer.demo.form',
        version: '1.0.0',
        stage: 'SURVEY',
        sections: [
          {
            sectionKey: 'base',
            title: '基础',
            fields: [
              {
                fieldKey: 'result.value',
                label: '结果',
                dataType: 'STRING',
                binding: 'task.input.result.value',
              },
            ],
          },
        ],
      },
      null,
      2,
    )
  }
  return JSON.stringify(
    {
      templateKey: 'platform.designer.demo.evidence',
      version: '1.0.0',
      title: '示例资料',
      stage: 'SURVEY',
      items: [
        {
          evidenceKey: 'site.panorama',
          name: '全景图',
          mediaType: 'PHOTO',
          required: true,
          capture: {
            allowCamera: true,
            allowGallery: false,
            requireRealtimeCapture: true,
            requireGps: true,
            watermarkFields: ['TIME', 'GPS', 'WORK_ORDER_NO'],
            minCount: 1,
            maxCount: 3,
            maxSizeBytes: 10485760,
          },
          qualityChecks: [{ checkType: 'BLUR', severity: 'BLOCK' }],
          reviewPolicy: { reviewRequired: true, allowItemLevelReject: true },
        },
      ],
    },
    null,
    2,
  )
}

async function createDraft() {
  busy.value = true
  error.value = null
  message.value = null
  try {
    const definitionJson = definitionText.value.trim() || defaultDefinition(assetType.value)
    const created = await createConfigurationDraft({
      assetType: assetType.value,
      assetKey: createKey.value.trim(),
      intendedSemanticVersion: createVersion.value.trim(),
      schemaVersion: '1.0.0',
      definitionJson,
    })
    selectDraft(created.data)
    message.value = '草稿已创建'
    await refreshList()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建草稿失败'
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
    selectDraft(updated.data)
    message.value = '草稿已保存'
    await refreshList()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
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
    if (selected.value.status === 'DRAFT' || selected.value.status === 'VALIDATED') {
      const updated = await updateConfigurationDraft(
        selected.value.draftId,
        definitionText.value,
        selected.value.aggregateVersion,
      )
      selectDraft(updated.data)
    }
    const validated = await validateConfigurationDraft(selected.value.draftId)
    selectDraft(validated.data)
    message.value = '校验通过'
    await refreshList()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '校验失败'
    await refreshList()
    if (selected.value) {
      const latest = drafts.value.find((d) => d.draftId === selected.value?.draftId)
      if (latest) selectDraft(latest)
    }
  } finally {
    busy.value = false
  }
}

async function loadDiff() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  try {
    diffView.value = await diffConfigurationDraft(selected.value.draftId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 Diff 失败'
  } finally {
    busy.value = false
  }
}

async function loadDependencies() {
  if (!selected.value || selected.value.assetType !== 'WORKFLOW') return
  busy.value = true
  error.value = null
  try {
    dependencyReport.value = await analyzeConfigurationDraftDependencies(selected.value.draftId)
    message.value = dependencyReport.value.complete ? '依赖完整' : '存在未满足依赖'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '依赖分析失败'
  } finally {
    busy.value = false
  }
}

async function runSimulation() {
  if (!selected.value || selected.value.assetType !== 'WORKFLOW') return
  busy.value = true
  error.value = null
  try {
    const result = await simulateConfigurationDraft(selected.value.draftId, {
      context: {
        workOrder: { clientCode: simClientCode.value || null, brandCode: null, serviceProductCode: null },
        region: { provinceCode: null, cityCode: null, districtCode: null },
        task: { stageCode: null, taskType: null },
        formValues: {},
      },
      maxSteps: 64,
    })
    simulationReport.value = result.data
    message.value = `模拟结果：${result.data.outcome}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '模拟失败'
  } finally {
    busy.value = false
  }
}

async function approveDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const approved = await approveConfigurationDraft(
      selected.value.draftId,
      approvalRef.value.trim(),
      selected.value.aggregateVersion,
    )
    selectDraft(approved.data)
    message.value = '审批通过'
    await refreshList()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批失败'
  } finally {
    busy.value = false
  }
}

async function publishDraft() {
  if (!selected.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const published = await publishConfigurationDraft(selected.value.draftId)
    selectDraft(published.data)
    message.value = `已发布版本 ${published.data.publishedVersionId}`
    await refreshList()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '发布失败'
  } finally {
    busy.value = false
  }
}

watch(assetType, async () => {
  selected.value = null
  definitionText.value = defaultDefinition(assetType.value)
  await refreshList()
})

onMounted(async () => {
  definitionText.value = defaultDefinition(assetType.value)
  await refreshList()
})
</script>

<template>
  <section class="designer" data-testid="configuration-designer">
    <header>
      <h1>配置设计器</h1>
      <p>草稿 → 校验 → 审批 → 发布。WORKFLOW 支持可视化拖拽画布（布局写入 metadata.layout）。</p>
    </header>

    <div class="toolbar">
      <label>
        资产类型
        <select v-model="assetType" data-testid="asset-type">
          <option value="WORKFLOW">WORKFLOW</option>
          <option value="FORM">FORM</option>
          <option value="EVIDENCE">EVIDENCE</option>
          <option value="SLA">SLA</option>
        </select>
      </label>
      <label>
        assetKey
        <input v-model="createKey" data-testid="asset-key" />
      </label>
      <label>
        version
        <input v-model="createVersion" data-testid="asset-version" />
      </label>
      <button type="button" :disabled="busy" data-testid="create-draft" @click="createDraft">新建草稿</button>
      <button type="button" :disabled="busy || !selected" data-testid="save-draft" @click="saveDraft">保存</button>
      <button type="button" :disabled="busy || !selected" data-testid="validate-draft" @click="validateDraft">校验</button>
      <button type="button" :disabled="busy || !selected" data-testid="diff-draft" @click="loadDiff">Diff</button>
      <button
        type="button"
        :disabled="busy || !selected || selected.assetType !== 'WORKFLOW'"
        data-testid="analyze-dependencies"
        @click="loadDependencies"
      >
        依赖分析
      </button>
      <label>
        模拟 clientCode
        <input v-model="simClientCode" data-testid="sim-client-code" />
      </label>
      <button
        type="button"
        :disabled="busy || !selected || selected.assetType !== 'WORKFLOW'"
        data-testid="simulate-draft"
        @click="runSimulation"
      >
        干跑模拟
      </button>
      <label>
        approvalRef
        <input v-model="approvalRef" data-testid="approval-ref" />
      </label>
      <button type="button" :disabled="busy || !selected || selected.status !== 'VALIDATED'" data-testid="approve-draft" @click="approveDraft">审批</button>
      <button type="button" :disabled="busy || !selected || selected.status !== 'APPROVED'" data-testid="publish-draft" @click="publishDraft">发布</button>
    </div>

    <p v-if="loading">加载中…</p>
    <p v-if="message" class="ok" data-testid="designer-message">{{ message }}</p>
    <p v-if="error" class="err" data-testid="designer-error">{{ error }}</p>

    <div class="layout" :class="{ 'with-canvas': showCanvas }">
      <aside>
        <h2>草稿列表</h2>
        <ul data-testid="draft-list">
          <li
            v-for="draft in drafts"
            :key="draft.draftId"
            :class="{ active: selected?.draftId === draft.draftId }"
            @click="selectDraft(draft)"
          >
            <strong>{{ draft.assetKey }}</strong>
            <span>{{ draft.status }} · v{{ draft.intendedSemanticVersion }}</span>
          </li>
        </ul>
      </aside>

      <main>
        <WorkflowCanvas
          v-if="showCanvas"
          :definition-json="definitionText"
          data-testid="workflow-preview"
          @update:definition-json="definitionText = $event"
        />
        <h2>定义 JSON</h2>
        <textarea
          v-model="definitionText"
          rows="16"
          data-testid="definition-json"
          spellcheck="false"
        />
        <div v-if="selected" class="meta">
          <span>状态：{{ selected.status }}</span>
          <span>版本：{{ selected.aggregateVersion }}</span>
          <span v-if="selected.approvalRef">审批：{{ selected.approvalRef }}</span>
          <span v-if="selected.publishedVersionId">已发布：{{ selected.publishedVersionId }}</span>
        </div>
        <ul v-if="selected?.validationErrors?.length" class="errors" data-testid="validation-errors">
          <li v-for="(item, index) in selected.validationErrors" :key="index">{{ item }}</li>
        </ul>
        <pre v-if="diffView" class="diff" data-testid="draft-diff">{{ diffView.unifiedDiff }}</pre>
        <div v-if="dependencyReport" class="deps" data-testid="dependency-report">
          <h3>依赖报告 · {{ dependencyReport.complete ? '完整' : '不完整' }}</h3>
          <ul>
            <li
              v-for="(item, index) in dependencyReport.dependencies"
              :key="index"
              :data-status="item.status"
            >
              <strong>{{ item.refField }}={{ item.refValue }}</strong>
              · {{ item.status }}
              <span v-if="item.sourceNodeId"> @{{ item.sourceNodeId }}</span>
              — {{ item.detail }}
            </li>
          </ul>
        </div>
        <div v-if="simulationReport" class="sim" data-testid="simulation-report">
          <h3>干跑轨迹 · {{ simulationReport.outcome }}</h3>
          <p>{{ simulationReport.message }}</p>
          <ol>
            <li v-for="step in simulationReport.steps" :key="step.index">
              [{{ step.nodeType }} {{ step.nodeId }}] {{ step.action }} — {{ step.detail }}
            </li>
          </ol>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.designer {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: end;
}
.toolbar label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
}
.layout {
  display: grid;
  grid-template-columns: 16rem 1fr;
  gap: 1rem;
}
.layout.with-canvas main {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
aside ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
aside li {
  border: 1px solid #d0d7de;
  border-radius: 0.4rem;
  padding: 0.5rem;
  margin-bottom: 0.5rem;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
aside li.active {
  border-color: #0969da;
  background: #ddf4ff;
}
.deps {
  border: 1px solid #d0d7de;
  border-radius: 0.4rem;
  padding: 0.75rem;
  background: #f6f8fa;
}
.deps ul {
  margin: 0.5rem 0 0;
  padding-left: 1.2rem;
}
.deps li[data-status='MISSING'] {
  color: #cf222e;
}
.deps li[data-status='SATISFIED'] {
  color: #1a7f37;
}
.sim {
  border: 1px solid #d0d7de;
  border-radius: 0.4rem;
  padding: 0.75rem;
  background: #fff8c5;
}
.sim ol {
  margin: 0.5rem 0 0;
  padding-left: 1.2rem;
}
textarea {
  width: 100%;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
}
.ok { color: #1a7f37; }
.err, .errors { color: #cf222e; }
.muted { color: #656d76; }
.meta { display: flex; gap: 1rem; font-size: 0.85rem; margin-top: 0.5rem; flex-wrap: wrap; }
.diff {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 0.4rem;
  overflow: auto;
  max-height: 20rem;
  font-size: 0.8rem;
}
@media (max-width: 1100px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
