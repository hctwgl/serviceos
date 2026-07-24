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
import BusinessTimeline from '../../../components/serviceos/BusinessTimeline.vue'
import FulfillmentStageBar from '../../../components/serviceos/FulfillmentStageBar.vue'
import ProjectMetricCard from '../../../components/serviceos/ProjectMetricCard.vue'
import ProjectSummaryHeader from '../../../components/serviceos/ProjectSummaryHeader.vue'
import RiskPanel from '../../../components/serviceos/RiskPanel.vue'
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
  { key: 'dashboard', label: '项目驾驶舱', description: '项目状态、阶段、风险与最新动态' },
  { key: 'fulfillment', label: '履约方案', description: '方案版本与流程设计' },
  { key: 'work-orders', label: '工单执行', description: '当前项目的履约工单' },
  { key: 'resources', label: '资源网络', description: '参与网点与服务范围' },
  { key: 'quality', label: '质量异常', description: '审核、整改与履约风险' },
  { key: 'analytics', label: '数据分析', description: '项目履约数据概览' },
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
const slaHealth = computed(() => {
  const total = project.value?.activeWorkOrderCount
  const risks = workOrders.data.value?.queueSummary.slaRiskCount
  if (total === null || total === undefined || risks === undefined) {
    return { label: '待同步', tone: 'default' as const, hint: '等待项目工单投影' }
  }
  const percentage = total === 0 ? 100 : Math.max(0, Math.round(((total - risks) / total) * 100))
  return {
    label: `${percentage}%`,
    tone: risks > 0 ? 'warning' as const : 'success' as const,
    hint: risks > 0 ? `${risks} 单存在时效风险` : '当前没有 SLA 风险',
  }
})
const timelineItems = computed(() => activities.value.map((activity) => ({
  id: activity.id,
  title: activity.label,
  time: activity.time,
  description: activity.detail,
  tone: activity.tone,
})))

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

function countLabel(value: number | null, suffix: string) {
  return value === null ? '暂不可见' : `${value}${suffix}`
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

      <section class="sos-project-context-strip" aria-label="项目上下文">
        <div><span>服务周期</span><strong>{{ projectPeriod(project.startsOn, project.endsOn) }}</strong></div>
        <div><span>项目编号</span><strong>{{ project.projectCode }}</strong></div>
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
        <section class="project-operations-grid">
          <main class="project-operations-main">
            <div class="project-ops-metric-row">
              <ProjectMetricCard label="运行工单" :value="countLabel(project.activeWorkOrderCount, ' 单')" hint="当前项目范围" tone="blue" />
              <ProjectMetricCard label="参与网点" :value="`${project.networkNames.length} 个`" hint="责任网点候选范围" />
              <ProjectMetricCard label="履约方案" :value="`${project.fulfillmentProfiles.length} 套`" hint="项目服务场景" tone="blue" />
              <ProjectMetricCard label="SLA 健康度" :value="slaHealth.label" :hint="slaHealth.hint" :tone="slaHealth.tone" />
            </div>

            <FulfillmentStageBar
              :stages="stageBar"
              title="履约阶段进度"
              description="沿用项目绑定方案与当前工单投影，不另建一套项目状态。"
            />

            <BusinessTimeline :items="timelineItems" :unavailable="dashboardUnavailable">
              <template #extra><RouterLink to="/work-orders">全部工单</RouterLink></template>
            </BusinessTimeline>
          </main>

          <aside class="project-operations-rail">
            <SlaHealthCard
              :risk-count="workOrders.data.value?.queueSummary.slaRiskCount"
              :total-count="project.activeWorkOrderCount"
              description="来自当前项目工单风险投影"
            />
            <RiskPanel
              :items="riskItems"
              :unavailable="dashboardUnavailable"
              title="需要项目经理关注"
              description="风险入口只负责发现，处置回到正式工单工作区。"
            />
            <section class="project-context-note">
              <span class="sos-eyebrow">OPERATING CONTEXT</span>
              <h2>履约责任边界</h2>
              <p>项目人员负责协同，平台分配责任网点，责任网点再分配责任师傅。不同责任层在工单工作区分别展示。</p>
              <RouterLink :to="`/projects/${project.projectId}/team-regions`">查看团队与区域分工</RouterLink>
            </section>
          </aside>
        </section>
      </template>

      <section v-else-if="activeTab === 'resources'" class="project-tab-surface">
        <header class="sos-panel-heading"><div><span class="sos-eyebrow">RESOURCE NETWORK</span><h2>资源网络</h2><p>当前项目参与的服务网点与服务范围只读展示。</p></div><RouterLink :to="`/projects/${project.projectId}/team-regions`">维护区域分工</RouterLink></header>
        <div class="project-resource-list"><span v-for="network in project.networkNames" :key="network">{{ network }}</span><em v-if="!project.networkNames.length">尚未配置参与网点</em></div>
      </section>

      <section v-else-if="activeTab === 'quality'" class="project-tab-surface project-tab-surface--split">
        <RiskPanel :items="riskItems" :unavailable="dashboardUnavailable" title="质量与履约异常" description="项目级风险由工单运行投影聚合，处置进入工单工作区。" />
        <div class="project-tab-callout"><span class="sos-eyebrow">QUALITY LOOP</span><h2>审核与整改</h2><p>当前 Admin 以工单工作区的审核整改区块作为事实入口，项目页不复制审核状态。</p><RouterLink to="/work-orders?view=review">进入审核与整改</RouterLink></div>
      </section>

      <section v-else-if="activeTab === 'analytics'" class="project-tab-surface">
        <header class="sos-panel-heading"><div><span class="sos-eyebrow">PROJECT SIGNALS</span><h2>数据分析</h2><p>只呈现现有查询可证明的项目运营信号，不生成未提供的精确百分比。</p></div></header>
        <div class="project-analytics-grid">
          <article><span>项目工单</span><strong>{{ workOrders.data.value?.totalCount ?? '—' }}</strong><small>当前查询返回总量</small></article>
          <article><span>SLA 风险</span><strong>{{ workOrders.data.value?.queueSummary.slaRiskCount ?? '—' }}</strong><small>进入风险工作区继续判断</small></article>
          <article><span>异常工单</span><strong>{{ workOrders.data.value?.queueSummary.exceptionCount ?? '—' }}</strong><small>只统计当前数据范围</small></article>
          <article><span>数据生成时间</span><strong>{{ workOrders.data.value?.generatedAt?.slice(0, 16).replace('T', ' ') ?? '—' }}</strong><small>服务端投影</small></article>
        </div>
      </section>

      <section v-else class="project-tab-surface project-tab-surface--handoff">
        <span class="sos-eyebrow">WORKSPACE HANDOFF</span>
        <h2>{{ activeTab === 'fulfillment' ? '履约方案设计器' : '工单执行中心' }}</h2>
        <p>{{ activeTab === 'fulfillment' ? '项目履约方案不在驾驶舱内平铺编辑，进入方案设计器完成流程、版本和发布准备。' : '项目工单的责任、阶段、SLA 和当前任务在工单工作区内完成判断与操作。' }}</p>
        <RouterLink class="primary-link-button" :to="activeTab === 'fulfillment' ? `/projects/${project.projectId}/fulfillment` : `/work-orders?projectId=${project.projectId}`">{{ activeTab === 'fulfillment' ? '打开方案设计器' : '打开项目工单' }}</RouterLink>
      </section>
    </template>
  </div>
</template>
