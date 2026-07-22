<script setup lang="ts">
import type { ProjectFulfillmentDocument } from '@serviceos/api-client'

import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Tag,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import { formatDateTime } from '../../../presenters/work-order'
import {
  useUpdateProjectFulfillmentDraftCommand,
  useValidateProjectFulfillmentDraftCommand,
} from '../commands/use-project-fulfillment-draft-commands'
import { useProjectFulfillmentDraftQuery } from '../queries/use-project-fulfillment-query'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))
const project = useProjectWorkspaceQuery(projectId)
const draft = useProjectFulfillmentDraftQuery(projectId, profileId)
const updateCommand = useUpdateProjectFulfillmentDraftCommand(
  () => projectId.value,
  () => profileId.value,
)
const validateCommand = useValidateProjectFulfillmentDraftCommand(
  () => projectId.value,
  () => profileId.value,
)
const model = reactive<{
  description: string
  document: ProjectFulfillmentDocument
  profileName: string
}>({
  description: '',
  document: { orderTypeName: null, schemaVersion: '1.0', stages: [] },
  profileName: '',
})
const saved = ref(false)

const ownerOptions = [
  { value: 'PLATFORM', label: '平台' },
  { value: 'NETWORK', label: '责任网点' },
  { value: 'TECHNICIAN', label: '责任师傅' },
  { value: 'SYSTEM', label: '系统自动执行' },
]

function cloneDocument(document: ProjectFulfillmentDocument): ProjectFulfillmentDocument {
  return {
    ...document,
    supportedClientKinds: document.supportedClientKinds
      ? [...document.supportedClientKinds]
      : undefined,
    stages: document.stages.map((stage) => ({
      ...stage,
      actions: stage.actions?.map((action) => ({ ...action })),
      evidenceRefs: stage.evidenceRefs ? [...stage.evidenceRefs] : undefined,
      exceptionPaths: stage.exceptionPaths?.map((path) => ({ ...path })),
      formRefs: stage.formRefs ? [...stage.formRefs] : undefined,
      transitions: stage.transitions?.map((transition) => ({ ...transition })),
    })),
  }
}

watch(
  () => draft.data.value,
  (value) => {
    if (!value) return
    model.profileName = value.profileName
    model.description = value.description ?? ''
    model.document = cloneDocument(value.document)
  },
  { immediate: true },
)

async function saveDraft() {
  if (!draft.data.value) return
  saved.value = false
  await updateCommand.mutateAsync({
    aggregateVersion: draft.data.value.aggregateVersion,
    profileName: model.profileName.trim(),
    description: model.description.trim() || undefined,
    document: model.document,
  })
  saved.value = true
  validateCommand.reset()
}

function issueColor(severity: string) {
  if (severity === 'ERROR') return 'error'
  if (severity === 'WARNING') return 'warning'
  return 'processing'
}

function serviceProductLabel(code: string) {
  if (code === 'HOME_CHARGING_SURVEY_INSTALL') return '家充勘测安装服务'
  return '项目服务产品'
}

function taskTypeLabel(taskType: string | null) {
  const labels: Record<string, string> = {
    DISPATCH: '派单协调',
    INSTALL: '上门安装',
    REVIEW: '资料审核',
    SURVEY: '现场勘测',
  }
  return taskType ? labels[taskType] ?? '业务任务' : '无独立任务'
}
</script>

<template>
  <Page
    :title="project.data.value ? `${project.data.value.projectName} · 配置草稿` : '履约配置草稿'"
    description="使用结构化业务表单编辑履约阶段和责任边界。"
    content-class="fulfillment-draft-page"
  >
    <template #extra>
      <Space>
        <RouterLink :to="`/projects/${projectId}/fulfillment?profileId=${profileId}`"><Button>返回配置概览</Button></RouterLink>
        <Button :loading="validateCommand.isPending.value" @click="validateCommand.mutate()">校验草稿</Button>
        <Button type="primary" :loading="updateCommand.isPending.value" @click="saveDraft">保存草稿</Button>
      </Space>
    </template>

    <PageError v-if="draft.isError.value" :detail="draft.error.value?.message ?? '配置草稿加载失败'" />
    <div v-else-if="draft.isLoading.value" class="page-loading">正在加载履约配置草稿…</div>
    <template v-else-if="draft.data.value">
      <div v-if="saved" class="product-notice success">草稿已保存。任何修改都会使之前的校验结果失效，发布前必须重新校验。</div>
      <PageError v-if="updateCommand.isError.value" :detail="updateCommand.error.value?.message ?? '草稿保存失败'" />
      <PageError v-if="validateCommand.isError.value" :detail="validateCommand.error.value?.message ?? '草稿校验失败'" />

      <div class="fulfillment-draft-layout">
        <Card class="fulfillment-draft-main" :bordered="false" title="基本信息与履约阶段">
          <div class="draft-basic-form">
            <label><span>方案名称</span><Input v-model:value="model.profileName" :maxlength="200" /></label>
            <label><span>工单类型名称</span><Input :value="model.document.orderTypeName ?? ''" placeholder="例如：家充安装工单" @update:value="(value) => model.document.orderTypeName = String(value)" /></label>
            <label class="draft-description"><span>方案说明</span><Input.TextArea v-model:value="model.description" :rows="3" :maxlength="2000" /></label>
          </div>

          <div class="draft-stage-heading">
            <div><h2>履约阶段</h2><p>按实际执行顺序配置每个阶段的名称、责任方和业务说明。</p></div>
            <Tag color="processing">{{ model.document.stages.length }} 个阶段</Tag>
          </div>
          <div class="draft-stage-list">
            <article v-for="stage in model.document.stages" :key="stage.stageCode" class="draft-stage-card">
              <header><b>{{ stage.sequence }}</b><div><strong>{{ stage.stageName }}</strong><span>第 {{ stage.sequence }} 个履约阶段</span></div><Tag v-if="stage.terminal" color="success">终态</Tag></header>
              <label><span>阶段名称</span><Input v-model:value="stage.stageName" :maxlength="120" /></label>
              <label><span>责任方</span><Select v-model:value="stage.ownerType" :options="ownerOptions" /></label>
              <label><span>业务说明</span><Input.TextArea :value="stage.description ?? ''" :rows="2" @update:value="(value) => stage.description = String(value)" /></label>
              <footer>
                <span>任务：{{ taskTypeLabel(stage.taskType) }}</span>
                <span>表单 {{ stage.formRefs?.length ?? 0 }} 份</span>
                <span>资料 {{ stage.evidenceRefs?.length ?? 0 }} 项</span>
              </footer>
            </article>
          </div>
        </Card>

        <aside class="fulfillment-draft-rail">
          <Card :bordered="false" title="草稿状态">
            <dl>
              <div><dt>服务产品</dt><dd>{{ serviceProductLabel(draft.data.value.serviceProductCode) }}</dd></div>
              <div><dt>并发版本</dt><dd>{{ draft.data.value.aggregateVersion }}</dd></div>
              <div><dt>最近更新</dt><dd>{{ formatDateTime(draft.data.value.updatedAt) }}</dd></div>
              <div><dt>结构版本</dt><dd>{{ model.document.schemaVersion }}</dd></div>
            </dl>
          </Card>
          <Card :bordered="false" title="发布边界">
            <p>流程、表单资料、SLA、派单、审核整改和集成规则必须整体校验、整体发布。</p>
            <p>新版本只影响发布后创建的新工单，进行中的工单继续使用创建时绑定的版本。</p>
          </Card>
          <Card v-if="validateCommand.data.value" :bordered="false" title="校验结果">
            <div v-if="!validateCommand.data.value.length" class="validation-success">未发现阻塞问题，可以继续影响分析。</div>
            <div v-else class="validation-issues">
              <article v-for="issue in validateCommand.data.value" :key="`${issue.errorCode}-${issue.fieldPath}`">
                <Tag :color="issueColor(issue.severity)">{{ issue.severity }}</Tag>
                <strong>{{ issue.userMessage }}</strong>
                <p v-if="issue.suggestion">{{ issue.suggestion }}</p>
              </article>
            </div>
          </Card>
        </aside>
      </div>
    </template>
  </Page>
</template>
