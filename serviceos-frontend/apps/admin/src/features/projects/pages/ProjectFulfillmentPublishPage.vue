<script setup lang="ts">
import type { ProjectFulfillmentCompareChange } from '@serviceos/api-client'

import {
  Button,
  Card,
  Checkbox,
  Input,
  Space,
  Steps,
  Tag,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useProjectFulfillmentPublishCommand } from '../commands/use-project-fulfillment-publish-command'
import { useProjectFulfillmentProfileQuery } from '../queries/use-project-fulfillment-query'
import { useProjectFulfillmentPublishQuery } from '../queries/use-project-fulfillment-publish-query'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))
const project = useProjectWorkspaceQuery(projectId)
const profile = useProjectFulfillmentProfileQuery(projectId, profileId)
const preparation = useProjectFulfillmentPublishQuery(projectId, profileId)
const publishCommand = useProjectFulfillmentPublishCommand(
  () => projectId.value,
  () => profileId.value,
)

const effectiveFrom = ref('')
const publishNote = ref('')
const confirmed = ref(false)

const blockingIssues = computed(() => (
  preparation.validation.data.value?.filter((issue) => issue.severity === 'ERROR') ?? []
))
const warningIssues = computed(() => (
  preparation.validation.data.value?.filter((issue) => issue.severity === 'WARNING') ?? []
))
const canPublish = computed(() => Boolean(
  profile.data.value?.allowedActions.includes('PUBLISH')
  && preparation.validation.data.value
  && !blockingIssues.value.length
  && preparation.preview.data.value
  && preparation.impact.data.value
  && confirmed.value
  && !publishCommand.isPending.value,
))
const currentStep = computed(() => {
  if (publishCommand.data.value) return 3
  if (!preparation.validation.data.value || blockingIssues.value.length) return 0
  if (!preparation.preview.data.value) return 1
  if (!preparation.impact.data.value) return 2
  return 3
})

const stepItems = [
  { title: '完整性校验' },
  { title: '运行预览' },
  { title: '影响分析' },
  { title: '确认发布' },
]

const categoryLabels: Record<ProjectFulfillmentCompareChange['category'], string> = {
  ACTION: '业务动作',
  DISPATCH: '派单规则',
  EVIDENCE: '资料要求',
  FORM: '业务表单',
  NOTIFICATION: '通知规则',
  OTHER: '其他配置',
  SLA: '时效规则',
  STAGE: '履约阶段',
  TASK: '业务任务',
}
const changeTypeLabels: Record<ProjectFulfillmentCompareChange['changeType'], string> = {
  ADDED: '新增',
  MODIFIED: '调整',
  REMOVED: '移除',
}

function severityLabel(severity: string) {
  if (severity === 'ERROR') return '阻塞问题'
  if (severity === 'WARNING') return '发布提醒'
  return '参考信息'
}

function localDateTimeToIso(value: string) {
  return value ? new Date(value).toISOString() : undefined
}

async function publishRevision() {
  if (!profile.data.value || !canPublish.value) return
  await publishCommand.mutateAsync({
    aggregateVersion: profile.data.value.aggregateVersion,
    effectiveFrom: localDateTimeToIso(effectiveFrom.value),
    publishNote: publishNote.value.trim() || undefined,
  })
  confirmed.value = false
}
</script>

<template>
  <Page
    :title="project.data.value ? `${project.data.value.projectName} · 版本与发布` : '版本与发布'"
    description="发布前统一检查履约流程、业务规则与存量工单影响。"
    content-class="fulfillment-publish-page"
  >
    <template #extra>
      <Space>
        <RouterLink :to="`/projects/${projectId}/fulfillment/${profileId}/draft`"><Button>返回配置草稿</Button></RouterLink>
        <Button
          :loading="preparation.validation.isFetching.value || preparation.preview.isFetching.value || preparation.impact.isFetching.value"
          @click="preparation.refreshPreparation"
        >
          重新检查
        </Button>
      </Space>
    </template>

    <PageError v-if="profile.isError.value" :detail="profile.error.value?.message ?? '履约配置加载失败'" />
    <div v-else-if="profile.isLoading.value" class="page-loading">正在准备发布检查…</div>
    <template v-else-if="profile.data.value">
      <Card class="publish-steps-card" :bordered="false">
        <Steps :current="currentStep" :items="stepItems" size="small" />
      </Card>

      <div v-if="publishCommand.data.value" class="product-notice success publish-success">
        <strong>履约配置 V{{ publishCommand.data.value.versionNo }} 已发布。</strong>
        新工单将使用新版本；已经创建的工单继续按照原绑定版本履约，不会被静默迁移。
      </div>
      <PageError v-if="publishCommand.isError.value" :detail="publishCommand.error.value?.message ?? '发布失败，当前生效版本未发生变化'" />

      <div class="publish-layout">
        <main class="publish-main">
          <Card :bordered="false" title="1. 完整性校验">
            <div v-if="preparation.validation.isLoading.value" class="section-loading">正在检查跨模块配置…</div>
            <PageError v-else-if="preparation.validation.isError.value" :detail="preparation.validation.error.value?.message ?? '完整性校验失败'" />
            <template v-else-if="preparation.validation.data.value">
              <div v-if="!preparation.validation.data.value.length" class="validation-success">未发现阻塞问题，配置结构完整。</div>
              <div v-else class="publish-issue-list">
                <article v-for="issue in preparation.validation.data.value" :key="`${issue.errorCode}-${issue.fieldPath}`">
                  <Tag :color="issue.severity === 'ERROR' ? 'error' : issue.severity === 'WARNING' ? 'warning' : 'processing'">{{ severityLabel(issue.severity) }}</Tag>
                  <div><strong>{{ issue.userMessage }}</strong><p v-if="issue.suggestion">{{ issue.suggestion }}</p></div>
                </article>
              </div>
            </template>
          </Card>

          <Card :bordered="false" title="2. 履约运行预览">
            <div v-if="preparation.preview.isLoading.value" class="section-loading">正在生成业务运行说明…</div>
            <PageError v-else-if="preparation.preview.isError.value" :detail="preparation.preview.error.value?.message ?? '运行预览生成失败'" />
            <template v-else-if="preparation.preview.data.value">
              <div class="runbook-summary">
                <div><span>方案</span><strong>{{ preparation.preview.data.value.runbook.profileName }}</strong></div>
                <div><span>服务产品</span><strong>{{ preparation.preview.data.value.runbook.serviceProductLabel }}</strong></div>
                <div><span>工单类型</span><strong>{{ preparation.preview.data.value.runbook.orderTypeName || '未单独命名' }}</strong></div>
                <div><span>履约阶段</span><strong>{{ preparation.preview.data.value.runbook.stageCount }} 个</strong></div>
              </div>
              <div class="runbook-stage-list">
                <article v-for="stage in preparation.preview.data.value.runbook.stages" :key="`${stage.sequence}-${stage.stageName}`">
                  <b>{{ stage.sequence }}</b>
                  <div class="runbook-stage-body">
                    <header><strong>{{ stage.stageName }}</strong><Tag v-if="stage.terminal" color="success">终态</Tag></header>
                    <p>{{ stage.ownerTypeLabel }}<span v-if="stage.taskTypeLabel"> · {{ stage.taskTypeLabel }}</span></p>
                    <footer>
                      <span>表单 {{ stage.formCount ?? 0 }} 份</span>
                      <span>资料 {{ stage.evidenceCount ?? 0 }} 项</span>
                      <span v-if="stage.slaSummary">{{ stage.slaSummary }}</span>
                      <span v-if="stage.nextStageSummary">{{ stage.nextStageSummary }}</span>
                    </footer>
                  </div>
                </article>
              </div>
              <p class="runbook-impact-summary">{{ preparation.preview.data.value.runbook.impactSummary }}</p>
            </template>
          </Card>

          <Card :bordered="false" title="3. 版本差异与影响">
            <div v-if="preparation.impact.isLoading.value" class="section-loading">正在分析版本影响…</div>
            <PageError v-else-if="preparation.impact.isError.value" :detail="preparation.impact.error.value?.message ?? '影响分析失败'" />
            <template v-else-if="preparation.impact.data.value">
              <div class="impact-scope-grid">
                <article><span>新工单</span><strong>{{ preparation.impact.data.value.impact.newWorkOrdersScope }}</strong></article>
                <article><span>已有工单</span><strong>{{ preparation.impact.data.value.impact.existingWorkOrdersScope }}</strong></article>
              </div>
              <div v-if="preparation.impact.data.value.changes.length" class="compare-change-list">
                <article v-for="change in preparation.impact.data.value.changes" :key="`${change.category}-${change.summary}`">
                  <div><Tag color="blue">{{ categoryLabels[change.category] }}</Tag><Tag>{{ changeTypeLabels[change.changeType] }}</Tag></div>
                  <strong>{{ change.summary }}</strong><p v-if="change.detail">{{ change.detail }}</p>
                </article>
              </div>
              <div v-else class="product-empty-inline">当前草稿与生效版本没有业务差异。</div>
              <div v-if="preparation.impact.data.value.risks.length" class="impact-risk-list">
                <strong>发布前提醒</strong><p v-for="risk in preparation.impact.data.value.risks" :key="risk">{{ risk }}</p>
              </div>
            </template>
          </Card>

          <Card :bordered="false" title="4. 确认发布">
            <div class="publish-form">
              <label><span>计划生效时间（可选）</span><Input v-model:value="effectiveFrom" type="datetime-local" /></label>
              <label><span>发布说明</span><Input.TextArea v-model:value="publishNote" :rows="3" :maxlength="500" placeholder="说明本次业务规则调整的目的" /></label>
              <Checkbox v-model:checked="confirmed">我已确认校验和影响分析结果，并理解已有工单不会迁移到新版本。</Checkbox>
              <div class="publish-submit-row">
                <span v-if="!profile.data.value.allowedActions.includes('PUBLISH')">当前账号没有发布该项目履约配置的权限。</span>
                <span v-else-if="blockingIssues.length">请先修复 {{ blockingIssues.length }} 个阻塞问题。</span>
                <span v-else-if="warningIssues.length">当前有 {{ warningIssues.length }} 项发布提醒，请确认后再发布。</span>
                <span v-else>发布将生成新的不可变版本。</span>
                <Button type="primary" :disabled="!canPublish" :loading="publishCommand.isPending.value" @click="publishRevision">发布新版本</Button>
              </div>
            </div>
          </Card>
        </main>

        <aside class="publish-rail">
          <Card :bordered="false" title="当前配置">
            <dl>
              <div><dt>方案名称</dt><dd>{{ profile.data.value.profileName }}</dd></div>
              <div><dt>当前生效版本</dt><dd>{{ profile.data.value.activeVersion ? `V${profile.data.value.activeVersion}` : '尚未发布' }}</dd></div>
              <div><dt>草稿状态</dt><dd>{{ profile.data.value.draftRevisionId ? '存在待发布草稿' : '无活动草稿' }}</dd></div>
              <div><dt>最近更新</dt><dd>{{ formatDateTime(profile.data.value.updatedAt) }}</dd></div>
            </dl>
          </Card>
          <Card :bordered="false" title="版本历史">
            <div v-if="preparation.revisions.isLoading.value" class="section-loading">正在加载版本历史…</div>
            <PageError v-else-if="preparation.revisions.isError.value" :detail="preparation.revisions.error.value?.message ?? '版本历史加载失败'" />
            <div v-else-if="preparation.revisions.data.value?.length" class="revision-list">
              <article v-for="revision in preparation.revisions.data.value" :key="revision.revisionId">
                <header><strong>V{{ revision.versionNo }}</strong><Tag color="success">已发布</Tag></header>
                <p>{{ formatDateTime(revision.publishedAt || revision.createdAt) }}</p>
                <span v-if="revision.publishedByDisplayName">{{ revision.publishedByDisplayName }}</span>
              </article>
            </div>
            <div v-else class="product-empty-inline">尚无已发布版本。</div>
          </Card>
          <Card :bordered="false" title="发布原则">
            <p>履约流程、表单资料、时效、派单和审核规则作为一个整体原子发布。</p>
            <p>发布失败时，当前生效版本保持不变，草稿继续保留。</p>
          </Card>
        </aside>
      </div>
    </template>
  </Page>
</template>
