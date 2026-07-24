<script setup lang="ts">
import type { AdminWorkOrderDirectoryView } from '@serviceos/api-client'
import { loadAdminWorkOrders } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import FulfillmentStageBar from '../../../components/serviceos/FulfillmentStageBar.vue'
import RiskPanel from '../../../components/serviceos/RiskPanel.vue'
import SlaHealthCard from '../../../components/serviceos/SlaHealthCard.vue'
import VersionBadge from '../../../components/serviceos/VersionBadge.vue'
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
const status = computed(() => project.value ? presentAdminProjectStatus(project.value.status) : null)
const stageBar = computed(() => buildProjectStageBar(project.value, project.value?.fulfillmentProfiles ?? [], workOrders.data.value))
const riskItems = computed(() => buildProjectRiskItems(workOrders.data.value))
const activities = computed(() => buildProjectActivities(workOrders.data.value?.items ?? []))
const dashboardUnavailable = computed(() => workOrders.isError.value || !workOrders.data.value)

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
      <header class="project-operations-header">
        <div class="project-operations-header__title">
          <p class="breadcrumb">客户与项目 / 项目管理 / 运营工作区</p>
          <div class="project-title-line">
            <h1>{{ project.projectName }}</h1>
            <StatusPill v-if="status" :tone="status.tone" :label="status.label" />
          </div>
          <p>{{ project.clientName ?? '客户品牌待确认' }} · {{ project.projectCode }} · 新能源现场服务网络</p>
        </div>
        <div class="project-operations-header__actions">
          <RouterLink class="secondary-link-button" :to="`/work-orders?projectId=${project.projectId}`">查看项目工单</RouterLink>
          <RouterLink class="primary-link-button" :to="`/projects/${project.projectId}/fulfillment`">进入方案设计器</RouterLink>
        </div>
      </header>

      <PageError v-if="!project.dataComplete" :detail="project.dataProblem ?? '项目页面数据不完整，部分事实暂不可用。'" />

      <section class="project-operations-summary" aria-label="项目摘要">
        <div class="project-summary-lead">
          <span>项目状态</span>
          <strong>{{ status?.label ?? '状态待确认' }}</strong>
          <small>{{ project.activeWorkOrderCount === null ? '运行工单数量暂不可见' : '项目当前运行范围' }}</small>
        </div>
        <div><span>服务周期</span><strong>{{ projectPeriod(project.startsOn, project.endsOn) }}</strong><small>项目合同周期</small></div>
        <div><span>客户品牌</span><strong>{{ project.clientName ?? '品牌待确认' }}</strong><small>项目协作方</small></div>
        <div><span>服务区域</span><strong>{{ project.regionNames.join('、') || '区域待配置' }}</strong><small>{{ project.regionNames.length }} 个区域</small></div>
        <div><span>当前履约方案</span><strong>{{ activeProfile?.profileName ?? '尚未建立方案' }}</strong><small>{{ activeProfile?.serviceProductName ?? '等待方案配置' }}</small></div>
        <div><span>当前版本</span><VersionBadge :status="activeProfile?.status" :version="activeProfile?.activeVersion" /><small>{{ activeProfile?.updatedAt ? `更新于 ${activeProfile.updatedAt.slice(0, 10)}` : '版本信息待确认' }}</small></div>
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
              <article><span>运行工单</span><strong>{{ countLabel(project.activeWorkOrderCount, ' 单') }}</strong><small>当前项目范围</small></article>
              <article><span>参与网点</span><strong>{{ project.networkNames.length }} 个</strong><small>责任网点候选范围</small></article>
              <article><span>履约方案</span><strong>{{ project.fulfillmentProfiles.length }} 套</strong><small>项目服务场景</small></article>
              <article><span>数据时间</span><strong>{{ project.asOf.slice(11, 16) || '—' }}</strong><small>服务端投影时间</small></article>
            </div>

            <FulfillmentStageBar
              :stages="stageBar"
              title="履约阶段进度"
              description="沿用项目绑定方案与当前工单投影，不另建一套项目状态。"
            />

            <section class="project-activity-panel">
              <header class="sos-panel-heading">
                <div><span class="sos-eyebrow">PROJECT ACTIVITY</span><h2>最近业务动态</h2><p>从当前项目工单投影汇总，点击进入单张工单查看完整事件。</p></div>
                <RouterLink to="/work-orders">全部工单</RouterLink>
              </header>
              <div v-if="dashboardUnavailable" class="sos-inline-unavailable"><strong>项目动态暂时无法获取</strong><span>工单投影恢复后会自动刷新，不显示伪造的“无动态”。</span></div>
              <div v-else-if="!activities.length" class="sos-inline-empty"><strong>当前项目还没有工单动态</strong><span>新工单受理后，最近业务事件会出现在这里。</span></div>
              <ol v-else class="project-activity-list">
                <li v-for="activity in activities" :key="activity.id" :class="`tone-${activity.tone}`">
                  <span class="project-activity-list__time">{{ activity.time }}</span>
                  <span class="project-activity-list__dot" />
                  <span class="project-activity-list__copy"><strong>{{ activity.label }}</strong><small>{{ activity.detail }}</small></span>
                </li>
              </ol>
            </section>
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
