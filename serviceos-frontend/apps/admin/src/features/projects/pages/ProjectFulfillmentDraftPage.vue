<script setup lang="ts">
import type { ProjectFulfillmentDocument } from '@serviceos/api-client'

import {
  Button,
  Card,
  Input,
  Space,
  Tag,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import { formatDateTime } from '../../../presenters/work-order'
import FulfillmentStageDesigner from '../components/FulfillmentStageDesigner.vue'
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
  document: {
    matchRule: { brandCodes: [], provinceCodes: [] },
    orderTypeName: null,
    schemaVersion: '1.0',
    stages: [],
  },
  profileName: '',
})
const saved = ref(false)

function cloneDocument(document: ProjectFulfillmentDocument): ProjectFulfillmentDocument {
  return {
    ...document,
    matchRule: {
      brandCodes: [...(document.matchRule?.brandCodes ?? [])],
      provinceCodes: [...(document.matchRule?.provinceCodes ?? [])],
    },
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

function parseCodes(value: unknown) {
  return String(value ?? '')
    .split(/[,，\s]+/)
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
    .filter((item, index, values) => values.indexOf(item) === index)
}

function updateMatchRule(
  dimension: 'brandCodes' | 'provinceCodes',
  value: unknown,
) {
  const current = model.document.matchRule ?? { brandCodes: [], provinceCodes: [] }
  model.document.matchRule = {
    ...current,
    [dimension]: parseCodes(value),
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
    sourceBundleId: draft.data.value.sourceBundleId,
    workflowAssetVersionId: draft.data.value.workflowAssetVersionId,
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
        <RouterLink :to="`/projects/${projectId}/fulfillment/${profileId}/publish`"><Button>版本与发布</Button></RouterLink>
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

          <section class="fulfillment-match-rule">
            <header>
              <div>
                <span>适用范围</span>
                <h2>结构化匹配条件</h2>
                <p>空值表示该维度不限制；正式受理时先硬匹配，再按优先级和规则具体度确定唯一方案。</p>
              </div>
              <Tag color="processing">
                {{ (model.document.matchRule?.brandCodes.length ?? 0) + (model.document.matchRule?.provinceCodes.length ?? 0) }} 个条件值
              </Tag>
            </header>
            <div class="fulfillment-match-rule-grid">
              <label>
                <span>客户品牌编码</span>
                <Input
                  :value="(model.document.matchRule?.brandCodes ?? []).join(', ')"
                  placeholder="例如：BYD_OCEAN；多个值用逗号分隔"
                  @update:value="(value) => updateMatchRule('brandCodes', value)"
                />
              </label>
              <label>
                <span>省级行政区编码</span>
                <Input
                  :value="(model.document.matchRule?.provinceCodes ?? []).join(', ')"
                  placeholder="例如：370000；多个值用逗号分隔"
                  @update:value="(value) => updateMatchRule('provinceCodes', value)"
                />
              </label>
            </div>
          </section>

          <FulfillmentStageDesigner v-model:stages="model.document.stages" />
        </Card>

        <aside class="fulfillment-draft-rail">
          <Card :bordered="false" title="运行绑定">
            <div
              class="runtime-binding-health"
              :class="{ ready: draft.data.value.workflowAssetVersionId && draft.data.value.sourceBundleId }"
            >
              <span aria-hidden="true" />
              <div>
                <strong>
                  {{ draft.data.value.workflowAssetVersionId && draft.data.value.sourceBundleId
                    ? '运行基础已绑定'
                    : '运行基础不完整' }}
                </strong>
                <p>
                  {{ draft.data.value.workflowAssetVersionId && draft.data.value.sourceBundleId
                    ? '保存草稿会保留 Workflow 与配置包引用；发布时还会核对阶段一致性。'
                    : '当前方案尚不能发布，请先建立 Workflow 与配置包。' }}
                </p>
              </div>
            </div>
          </Card>
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
