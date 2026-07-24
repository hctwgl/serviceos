<script setup lang="ts">
import type { AdminWorkOrderDirectoryView } from '@serviceos/api-client'
import {
  loadAdminWorkOrders,
  loadProjectFulfillmentDraft,
  loadProjectFulfillmentProfile,
} from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import FulfillmentStageBar from '../../../components/serviceos/FulfillmentStageBar.vue'
import ProjectRiskPanel from '../../../components/serviceos/ProjectRiskPanel.vue'
import ProjectSummaryHeader from '../../../components/serviceos/ProjectSummaryHeader.vue'
import ProjectTimeline from '../../../components/serviceos/ProjectTimeline.vue'
import SlaHealthCard from '../../../components/serviceos/SlaHealthCard.vue'
import {
  buildProjectActivities,
  buildProjectRiskItems,
  buildProjectStageBar,
  presentAdminProjectStatus,
} from '../presenters/project-operations'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

type ProjectTab = 'dashboard' | 'fulfillment' | 'work-orders' | 'resources' | 'quality' | 'analytics'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const workspace = useProjectWorkspaceQuery(projectId)
const project = computed(() => workspace.data.value)

const workOrders = useQuery<AdminWorkOrderDirectoryView>({
  queryKey: computed(() => ['admin-project-workorders', projectId.value]),
  queryFn: () => loadAdminWorkOrders({ projectId: projectId.value, limit: 12 }),
  enabled: computed(() => Boolean(projectId.value)),
})

const tabs: Array<{ key: ProjectTab; label: string; description: string }> = [
  { key: 'dashboard', label: '项目概览', description: '状态、风险、阶段、事件' },
  { key: 'fulfillment', label: '履约方案', description: '版本与流程设计' },
  { key: 'work-orders', label: '工单管理', description: '责任与当前任务' },
  { key: 'resources', label: '资源网络', description: '网点与服务区域' },
  { key: 'quality', label: '质量异常', description: '审核与整改入口' },
  { key: 'analytics', label: '数据分析', description: '运营信号' },
]

const activeTab = computed<ProjectTab>(() => {
  const requested = String(route.query.tab ?? 'dashboard') as ProjectTab
  return tabs.some((tab) => tab.key === requested) ? requested : 'dashboard'
})
const activeProfile = computed(() => (
  project.value?.fulfillmentProfiles.find((profile) => profile.status === 'ACTIVE')
    ?? project.value?.fulfillmentProfiles[0]
))
const activeProfileDetail = useQuery({
  queryKey: computed(() => ['admin-project-active-fulfillment-profile', projectId.value, activeProfile.value?.profileId]),
  queryFn: () => loadProjectFulfillmentProfile(projectId.value, activeProfile.value!.profileId),
  enabled: computed(() => Boolean(projectId.value && activeProfile.value?.profileId)),
})
const fulfillmentDraft = useQuery({
  queryKey: computed(() => ['admin-project-active-fulfillment-draft', projectId.value, activeProfile.value?.profileId]),
  queryFn: () => loadProjectFulfillmentDraft(projectId.value, activeProfile.value!.profileId),
  enabled: computed(() => Boolean(projectId.value && activeProfile.value?.profileId && activeProfileDetail.data.value?.draftRevisionId)),
})
const status = computed(() => project.value ? presentAdminProjectStatus(project.value.status) : null)
const stageBar = computed(() => buildProjectStageBar(
  project.value,
  project.value?.fulfillmentProfiles ?? [],
  workOrders.data.value,
  fulfillmentDraft.data.value?.document.stages,
))
const riskItems = computed(() => buildProjectRiskItems(workOrders.data.value))
const activities = computed(() => buildProjectActivities(workOrders.data.value?.items ?? []))
const dashboardUnavailable = computed(() => workOrders.isError.value || !workOrders.data.value)
const timelineItems = computed(() => activities.value.map((activity) => ({
  id: activity.id,
  title: activity.label,
  time: activity.time,
  description: activity.detail,
  tone: activity.tone,
})))

const nextAction = computed(() => {
  const risk = riskItems.value[0]
  if (risk) {
    return {
      label: `处理${risk.label}`,
      detail: risk.description,
      actionLabel: '打开处置入口',
      to: risk.to ?? '/work-orders',
    }
  }
  if (activeProfileDetail.data.value?.draftRevisionId) {
    return {
      label: '完成方案发布校验',
      detail: '活动草稿尚未成为新生效版本。',
      actionLabel: '进入发布检查',
      to: `/projects/${projectId.value}/fulfillment/${activeProfile.value?.profileId}/publish`,
    }
  }
  if (!activeProfile.value) {
    return {
      label: '建立履约方案',
      detail: '项目尚未绑定可用于受理的履约方案。',
      actionLabel: '打开方案设计器',
      to: `/projects/${projectId.value}/fulfillment`,
    }
  }
  return workOrders.data.value?.items.length
    ? {
        label: '查看当前项目工单',
        detail: '继续判断责任、阶段和时效。',
        actionLabel: '打开工单工作区',
        to: `/work-orders?projectId=${projectId.value}`,
      }
    : {
        label: '检查履约方案',
        detail: '确认流程、责任和版本发布状态。',
        actionLabel: '打开方案设计器',
        to: `/projects/${projectId.value}/fulfillment`,
      }
})

function openTab(tab: ProjectTab) {
  if (tab === 'fulfillment') {
    void router.push(`/projects/${projectId.value}/fulfillment`)
    return
  }
  if (tab === 'work-orders') {
    void router.push({ path: '/work-orders', query: { projectId: projectId.value } })
    return
  }
  void router.replace({ query: { ...route.query, tab } })
}

function projectPeriod(startsOn: string, endsOn: string | null) {
  return `${startsOn} 至 ${endsOn ?? '长期有效'}`
}
</script>

<template>
  <div class="project-operations-page">
    <PageError v-if="workspace.isError.value" :detail="workspace.error.value?.message ?? '项目工作区加载失败'" />
    <div v-else-if="workspace.isLoading.value" class="page-loading">正在加载新能源履约项目…</div>
    <template v-else-if="project">
      <ProjectSummaryHeader
        :project-name="project.projectName"
        :project-code="project.projectCode"
        :client-name="project.clientName ?? '客户品牌待确认'"
        :region-label="project.regionNames.join('、') || '服务区域待配置'"
        :status-label="status?.label ?? '状态待确认'"
        :status-tone="status?.tone ?? 'gray'"
        :fulfillment-name="activeProfile?.profileName ?? '尚未建立方案'"
        :version="activeProfile?.activeVersion"
      >
        <template #actions>
          <RouterLink class="secondary-link-button" :to="`/work-orders?projectId=${project.projectId}`">查看项目工单</RouterLink>
          <RouterLink class="primary-link-button" :to="`/projects/${project.projectId}/fulfillment`">进入方案设计器</RouterLink>
        </template>
      </ProjectSummaryHeader>

      <PageError v-if="!project.dataComplete" :detail="project.dataProblem ?? '项目页面数据不完整，部分事实暂不可用。'" />

      <section class="sos-project-context-strip" aria-label="项目周期">
        <div><span>服务周期</span><strong>{{ projectPeriod(project.startsOn, project.endsOn) }}</strong></div>
        <div><span>数据更新时间</span><strong>{{ project.asOf.slice(0, 16).replace('T', ' ') }}</strong></div>
      </section>

      <nav class="project-operations-tabs" aria-label="项目业务视图">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          :aria-current="activeTab === tab.key ? 'page' : undefined"
          @click="openTab(tab.key)"
        >
          <strong>{{ tab.label }}</strong>
          <small>{{ tab.description }}</small>
        </button>
      </nav>

      <template v-if="activeTab === 'dashboard'">
        <section class="project-next-action" aria-label="项目下一动作">
          <div>
            <span class="sos-eyebrow">下一动作</span>
            <strong>{{ nextAction.label }}</strong>
            <p>{{ nextAction.detail }}</p>
          </div>
          <RouterLink class="primary-link-button" :to="nextAction.to">{{ nextAction.actionLabel }}</RouterLink>
        </section>

        <section class="project-operations-grid">
          <main class="project-operations-main">
            <FulfillmentStageBar :stages="stageBar" title="履约状态" description="按当前方案和工单投影定位项目所在阶段。" />
            <ProjectTimeline :items="timelineItems" :unavailable="dashboardUnavailable">
              <template #extra><RouterLink to="/work-orders">全部业务事件</RouterLink></template>
            </ProjectTimeline>
          </main>

          <aside class="project-operations-rail">
            <ProjectRiskPanel :items="riskItems" :unavailable="dashboardUnavailable" />
            <SlaHealthCard
              :risk-count="workOrders.data.value?.queueSummary.slaRiskCount"
              :total-count="project.activeWorkOrderCount"
              description="当前项目工单时效投影"
            />
            <section class="project-context-note project-context-note--compact">
              <span class="sos-eyebrow">责任网络</span>
              <strong>{{ project.networkNames.length ? `${project.networkNames.length} 个参与网点` : '参与网点待配置' }}</strong>
              <RouterLink :to="`/projects/${project.projectId}/team-regions`">查看资源网络</RouterLink>
            </section>
          </aside>
        </section>
      </template>

      <section v-else-if="activeTab === 'resources'" class="project-tab-surface">
        <header class="sos-panel-heading"><div><span class="sos-eyebrow">资源网络</span><h2>参与网点与服务区域</h2></div><RouterLink :to="`/projects/${project.projectId}/team-regions`">维护区域分工</RouterLink></header>
        <div class="project-resource-list"><span v-for="network in project.networkNames" :key="network">{{ network }}</span><em v-if="!project.networkNames.length">尚未配置参与网点</em></div>
      </section>

      <section v-else-if="activeTab === 'quality'" class="project-tab-surface project-tab-surface--split">
        <ProjectRiskPanel :items="riskItems" :unavailable="dashboardUnavailable" />
        <div class="project-tab-callout"><span class="sos-eyebrow">质量闭环</span><h2>审核与整改</h2><p>项目级异常在工单工作区完成判断和处置。</p><RouterLink to="/work-orders?view=review">进入审核与整改</RouterLink></div>
      </section>

      <section v-else-if="activeTab === 'analytics'" class="project-tab-surface">
        <header class="sos-panel-heading"><div><span class="sos-eyebrow">项目运营信号</span><h2>数据分析</h2></div></header>
        <div class="project-analytics-grid">
          <article><span>项目工单</span><strong>{{ workOrders.data.value?.totalCount ?? '—' }}</strong><small>当前查询返回总量</small></article>
          <article><span>SLA 风险</span><strong>{{ workOrders.data.value?.queueSummary.slaRiskCount ?? '—' }}</strong><small>进入风险工作区继续判断</small></article>
          <article><span>异常工单</span><strong>{{ workOrders.data.value?.queueSummary.exceptionCount ?? '—' }}</strong><small>当前数据范围</small></article>
          <article><span>数据生成时间</span><strong>{{ workOrders.data.value?.generatedAt?.slice(0, 16).replace('T', ' ') ?? '—' }}</strong><small>服务端投影</small></article>
        </div>
      </section>

      <section v-else class="project-tab-surface project-tab-surface--handoff">
        <span class="sos-eyebrow">工作入口</span>
        <h2>{{ activeTab === 'fulfillment' ? '履约方案设计器' : '工单执行中心' }}</h2>
        <p>{{ activeTab === 'fulfillment' ? '流程、任务、表单、证据和 SLA 在方案工作台中作为一个版本设计和发布。' : '责任、阶段、SLA 和当前任务在工单工作区内完成判断与操作。' }}</p>
        <RouterLink class="primary-link-button" :to="activeTab === 'fulfillment' ? `/projects/${project.projectId}/fulfillment` : `/work-orders?projectId=${project.projectId}`">{{ activeTab === 'fulfillment' ? '打开方案设计器' : '打开项目工单' }}</RouterLink>
      </section>
    </template>
  </div>
</template>
