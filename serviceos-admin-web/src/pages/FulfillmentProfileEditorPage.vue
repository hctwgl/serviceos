<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  Select,
  Space,
  Tabs,
  TabPane,
  Tag,
} from 'ant-design-vue'
import { ArrowLeftOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import ConfigurationPageLayout from '../patterns/templates/ConfigurationPageLayout.vue'
import StickyActionBar from '../patterns/StickyActionBar.vue'
import {
  getProjectFulfillmentDraft,
  getProjectFulfillmentProfile,
  updateProjectFulfillmentDraft,
  validateProjectFulfillmentDraft,
  type ProjectFulfillmentDraft,
  type ProjectFulfillmentProfileDetail,
  type ProjectFulfillmentValidationIssue,
} from '../api/fulfillmentProfiles'
import { isConflictError } from '../api/client'
import { toUserFacingError } from '../product/errorMessages'
import { labelServiceProduct } from '../presentation/enum-labels'

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

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const error = ref<string | null>(null)
const conflict = ref(false)
const message = ref<string | null>(null)
const detail = ref<ProjectFulfillmentProfileDetail | null>(null)
const draft = ref<ProjectFulfillmentDraft | null>(null)
const issues = ref<ProjectFulfillmentValidationIssue[]>([])
const stages = ref<StageDoc[]>([])
const selectedStageCode = ref<string | null>(null)
const profileName = ref('')
const description = ref('')
const activeTab = ref('rules')
const dirty = ref(false)

const selectedStage = computed(
  () => stages.value.find((s) => s.stageCode === selectedStageCode.value) ?? null,
)

const stageErrors = computed(() => {
  const map = new Map<string, number>()
  for (const issue of issues.value) {
    if (issue.severity !== 'ERROR' || !issue.stageCode) continue
    map.set(issue.stageCode, (map.get(issue.stageCode) ?? 0) + 1)
  }
  return map
})

function parseStages(documentJson: string): StageDoc[] {
  try {
    const doc = JSON.parse(documentJson) as { stages?: StageDoc[] }
    return [...(doc.stages ?? [])].sort((a, b) => a.sequence - b.sequence)
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
    stages: stages.value.map((stage, index) => ({
      ...stage,
      sequence: index + 1,
    })),
  })
}

async function load() {
  loading.value = true
  error.value = null
  conflict.value = false
  try {
    detail.value = (await getProjectFulfillmentProfile(projectId.value, profileId.value)).data
    draft.value = (await getProjectFulfillmentDraft(projectId.value, profileId.value)).data
    profileName.value = draft.value.profileName
    description.value = draft.value.description ?? ''
    stages.value = parseStages(draft.value.documentJson)
    selectedStageCode.value = stages.value[0]?.stageCode ?? null
    dirty.value = false
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

function markDirty() {
  dirty.value = true
  message.value = null
}

function updateSelectedStage(mutator: (stage: StageDoc) => void) {
  const stage = selectedStage.value
  if (!stage) return
  mutator(stage)
  markDirty()
}

function addStage() {
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
  stages.value = stages.value.filter((s) => s.stageCode !== code)
  if (selectedStageCode.value === code) {
    selectedStageCode.value = stages.value[0]?.stageCode ?? null
  }
  markDirty()
}

function moveStage(code: string, delta: number) {
  const index = stages.value.findIndex((s) => s.stageCode === code)
  const target = index + delta
  if (index < 0 || target < 0 || target >= stages.value.length) return
  const copy = [...stages.value]
  const [item] = copy.splice(index, 1)
  copy.splice(target, 0, item)
  stages.value = copy.map((stage, i) => ({ ...stage, sequence: i + 1 }))
  markDirty()
}

async function saveDraft() {
  if (!draft.value) return
  saving.value = true
  error.value = null
  conflict.value = false
  try {
    const updated = await updateProjectFulfillmentDraft(
      projectId.value,
      profileId.value,
      draft.value.aggregateVersion,
      {
        profileName: profileName.value,
        description: description.value,
        documentJson: buildDocumentJson(),
        workflowAssetVersionId: draft.value.workflowAssetVersionId ?? undefined,
        sourceBundleId: draft.value.sourceBundleId ?? undefined,
      },
    )
    draft.value = updated.data
    dirty.value = false
    message.value = '草稿已保存'
  } catch (err) {
    if (isConflictError(err)) {
      conflict.value = true
      error.value = '配置已被他人更新。已保留您的本地修改，请复制后重新加载。'
    } else {
      error.value = toUserFacingError(err).message
    }
  } finally {
    saving.value = false
  }
}

async function validate() {
  validating.value = true
  error.value = null
  try {
    if (dirty.value) {
      await saveDraft()
    }
    issues.value = (await validateProjectFulfillmentDraft(projectId.value, profileId.value)).data
    const blocked = issues.value.filter((i) => i.severity === 'ERROR').length
    message.value = blocked
      ? `校验完成：${blocked} 个阻断错误`
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

watch([projectId, profileId], () => load())
onMounted(load)
</script>

<template>
  <ConfigurationPageLayout
    title="履约配置编辑工作区"
    description="配置阶段责任、表单、资料、动作与 SLA。技术编码进入高级区，普通运营使用中文业务字段。"
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
        返回
      </Button>
    </template>
    <template #primary-action>
      <Space>
        <Button :loading="validating" @click="validate">验证配置</Button>
        <Button
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW',
              params: { id: projectId, profileId },
            })
          "
        >
          预览
        </Button>
        <Button
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
        type="success"
        show-icon
        :message="message"
        style="margin-bottom: 12px"
      />
      <Alert
        v-if="conflict"
        type="warning"
        show-icon
        message="并发冲突"
        description="请复制本地修改后重新加载服务端最新草稿。"
        style="margin-bottom: 12px"
      />
    </template>

    <template #context>
      <p v-if="detail">
        工单类型：{{ labelServiceProduct(detail.serviceProductCode) }} · 状态：{{ detail.status }}
        · {{ dirty ? '草稿未保存' : '草稿已暂存' }}
      </p>
    </template>

    <template #version>
      <p v-if="draft">
        草稿修订：本地编辑中 · 聚合版本 {{ draft.aggregateVersion }} · 当前发布版本
        {{ detail?.activeVersion || '无' }}
      </p>
      <p>
        校验错误 {{ issues.filter((i) => i.severity === 'ERROR').length }} · 警告
        {{ issues.filter((i) => i.severity === 'WARNING').length }}
      </p>
    </template>

    <div v-if="!loading" class="editor-grid">
      <aside class="editor-grid__nav" aria-label="阶段导航">
        <div class="nav-head">
          <strong>阶段</strong>
          <Button size="small" @click="addStage" aria-label="新增阶段">
            <template #icon><PlusOutlined /></template>
          </Button>
        </div>
        <button
          v-for="stage in stages"
          :key="stage.stageCode"
          type="button"
          class="stage-item"
          :class="{ 'stage-item--active': stage.stageCode === selectedStageCode }"
          @click="selectedStageCode = stage.stageCode"
        >
          <span>{{ stage.sequence }}. {{ stage.stageName }}</span>
          <Tag v-if="stageErrors.get(stage.stageCode)" color="error">
            {{ stageErrors.get(stage.stageCode) }}
          </Tag>
        </button>
        <Space wrap style="margin-top: 12px">
          <Button
            size="small"
            :disabled="!selectedStageCode"
            @click="selectedStageCode && moveStage(selectedStageCode, -1)"
          >
            上移
          </Button>
          <Button
            size="small"
            :disabled="!selectedStageCode"
            @click="selectedStageCode && moveStage(selectedStageCode, 1)"
          >
            下移
          </Button>
          <Button
            size="small"
            danger
            :disabled="!selectedStageCode"
            @click="selectedStageCode && removeStage(selectedStageCode)"
          >
            删除
          </Button>
        </Space>
      </aside>

      <section class="editor-grid__main" aria-label="阶段配置">
        <Form layout="vertical" v-if="selectedStage">
          <Tabs v-model:activeKey="activeTab">
            <TabPane key="rules" tab="阶段规则">
              <FormItem label="阶段名称">
                <Input v-model:value="selectedStage.stageName" @change="markDirty" />
              </FormItem>
              <FormItem label="阶段说明">
                <Input.TextArea
                  v-model:value="selectedStage.description"
                  :rows="3"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="任务类型（业务）">
                <Select
                  v-model:value="selectedStage.taskType"
                  style="width: 100%"
                  :options="[
                    { value: 'DISPATCH', label: '派单调度' },
                    { value: 'SURVEY', label: '现场勘测' },
                    { value: 'INSTALL', label: '上门安装' },
                    { value: 'REVIEW', label: '审核' },
                    { value: 'FIELD_TASK', label: '现场任务' },
                  ]"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="阶段责任类型">
                <Select
                  v-model:value="selectedStage.ownerType"
                  style="width: 100%"
                  :options="[
                    { value: 'PLATFORM', label: '平台运营' },
                    { value: 'NETWORK', label: '合作网点' },
                    { value: 'TECHNICIAN', label: '师傅' },
                  ]"
                  @change="markDirty"
                />
              </FormItem>
              <FormItem label="顺序">
                <InputNumber :value="selectedStage.sequence" disabled style="width: 100%" />
              </FormItem>
            </TabPane>
            <TabPane key="forms" tab="表单">
              <Alert
                type="info"
                show-icon
                message="选择已发布 FORM 资产键；详细字段编辑复用配置设计器资产。"
                style="margin-bottom: 12px"
              />
              <FormItem label="表单引用（每行一个资产键）">
                <Input.TextArea
                  :value="(selectedStage.formRefs ?? []).join('\n')"
                  :rows="6"
                  placeholder="例如 survey.form.v1"
                  @change="
                    (e: Event) => {
                      const value = (e.target as HTMLTextAreaElement).value
                      updateSelectedStage((stage) => {
                        stage.formRefs = value
                          .split('\n')
                          .map((x) => x.trim())
                          .filter(Boolean)
                      })
                    }
                  "
                />
              </FormItem>
            </TabPane>
            <TabPane key="evidence" tab="资料要求">
              <FormItem label="资料模板引用（每行一个资产键）">
                <Input.TextArea
                  :value="(selectedStage.evidenceRefs ?? []).join('\n')"
                  :rows="6"
                  placeholder="例如 install.evidence.v1"
                  @change="
                    (e: Event) => {
                      const value = (e.target as HTMLTextAreaElement).value
                      updateSelectedStage((stage) => {
                        stage.evidenceRefs = value
                          .split('\n')
                          .map((x) => x.trim())
                          .filter(Boolean)
                      })
                    }
                  "
                />
              </FormItem>
            </TabPane>
            <TabPane key="actions" tab="动作与流转">
              <Alert
                type="info"
                show-icon
                message="动作从业务目录选择；最终是否可执行以服务端 allowed-actions 为准。"
                style="margin-bottom: 12px"
              />
              <FormItem label="成功后目标阶段编码">
                <Select
                  :value="
                    String(
                      ((selectedStage?.transitions?.[0] as { targetStage?: string } | undefined)
                        ?.targetStage ?? ''),
                    ) || undefined
                  "
                  allow-clear
                  style="width: 100%"
                  :options="
                    stages
                      .filter((s) => s.stageCode !== selectedStageCode)
                      .map((s) => ({ value: s.stageCode, label: s.stageName }))
                  "
                  @change="
                    (value: unknown) => {
                      const target = typeof value === 'string' ? value : ''
                      updateSelectedStage((stage) => {
                        stage.transitions = target
                          ? [{ targetStage: target, kind: 'NORMAL' }]
                          : []
                        stage.actions = target
                          ? [
                              {
                                actionCode: 'COMPLETE_STAGE',
                                actionLabel: '完成并进入下一阶段',
                                targetStage: target,
                              },
                            ]
                          : []
                      })
                    }
                  "
                />
              </FormItem>
            </TabPane>
            <TabPane key="owner" tab="责任与派单">
              <p class="muted">默认责任类型见「阶段规则」。派单策略复用既有 DISPATCH 资产绑定（高级）。</p>
            </TabPane>
            <TabPane key="review" tab="审核">
              <p class="muted">审核绑定复用 ReviewCase 运行时；阶段任务类型选择「审核」即可纳入终审链路。</p>
            </TabPane>
            <TabPane key="sla" tab="SLA">
              <FormItem label="SLA 策略资产键">
                <Input
                  :value="selectedStage.slaRef ?? ''"
                  placeholder="例如 task.elapsed.standard"
                  @change="
                    (e: Event) => {
                      const value = (e.target as HTMLInputElement).value.trim() || null
                      updateSelectedStage((stage) => {
                        stage.slaRef = value
                      })
                    }
                  "
                />
              </FormItem>
            </TabPane>
            <TabPane key="exceptions" tab="异常路径">
              <FormItem label="异常路径（逗号分隔：资料缺失,等待客户,无法施工）">
                <Input
                  :value="
                    (selectedStage.exceptionPaths ?? [])
                      .map((p) => String((p as { label?: string }).label ?? ''))
                      .filter(Boolean)
                      .join(',')
                  "
                  @change="
                    (e: Event) => {
                      const value = (e.target as HTMLInputElement).value
                      updateSelectedStage((stage) => {
                        stage.exceptionPaths = value
                          .split(',')
                          .map((x) => x.trim())
                          .filter(Boolean)
                          .map((label) => ({ label, trigger: label }))
                      })
                    }
                  "
                />
              </FormItem>
            </TabPane>
          </Tabs>
        </Form>
        <Alert v-else type="info" show-icon message="请选择左侧阶段，或新增阶段开始配置。" />
      </section>

      <aside class="editor-grid__side" aria-label="校验与发布摘要">
        <h3>方案概要</h3>
        <Form layout="vertical">
          <FormItem label="配置名称">
            <Input v-model:value="profileName" @change="markDirty" />
          </FormItem>
          <FormItem label="业务说明">
            <Input.TextArea v-model:value="description" :rows="3" @change="markDirty" />
          </FormItem>
        </Form>
        <h3>校验结果</h3>
        <ul class="issue-list">
          <li v-for="(issue, idx) in issues" :key="idx">
            <Button type="link" @click="jumpToIssue(issue)">
              [{{ issue.severity }}] {{ issue.userMessage }}
            </Button>
          </li>
          <li v-if="!issues.length" class="muted">尚未校验或无问题</li>
        </ul>
      </aside>
    </div>

    <StickyActionBar>
      <Button @click="load">重新加载</Button>
      <Button type="primary" :loading="saving" @click="saveDraft">
        <template #icon><SaveOutlined /></template>
        保存草稿
      </Button>
      <Button :loading="validating" @click="validate">验证配置</Button>
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
.editor-grid__nav,
.editor-grid__side,
.editor-grid__main {
  border: 1px solid var(--sos-color-border-light);
  border-radius: 8px;
  padding: 12px;
  background: var(--sos-color-surface-card, #fff);
}
.nav-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.stage-item {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 6px;
  padding: 8px 10px;
  border: 1px solid var(--sos-color-border-light);
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.stage-item--active {
  border-color: var(--sos-color-action-primary, #1677ff);
  background: var(--sos-color-surface-selected, #e6f4ff);
}
.issue-list {
  list-style: none;
  padding: 0;
  margin: 0;
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
