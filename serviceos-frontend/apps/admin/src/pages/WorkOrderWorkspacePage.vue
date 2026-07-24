<script setup lang="ts">
import type { WorkOrderWorkspaceTask } from '@serviceos/api-client'
import { loadAdminWorkOrderWorkspace, loadWorkspaceSection } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import {
  Card,
  ClockCircleOutlined,
  CopyOutlined,
  Space,
  Tabs,
} from '@serviceos/design-system'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Page } from '@vben/common-ui'
import AppointmentsVisitsSection from '../components/AppointmentsVisitsSection.vue'
import AssignmentDrawer from '../components/AssignmentDrawer.vue'
import FormsEvidenceSection from '../components/FormsEvidenceSection.vue'
import IntegrationSection from '../components/IntegrationSection.vue'
import PageError from '../components/PageError.vue'
import ReviewsCorrectionsSection from '../components/ReviewsCorrectionsSection.vue'
import FulfillmentStageBar from '../components/serviceos/FulfillmentStageBar.vue'
import RiskPanel from '../components/serviceos/RiskPanel.vue'
import SlaHealthCard from '../components/serviceos/SlaHealthCard.vue'
import TaskCard from '../components/serviceos/TaskCard.vue'
import VersionBadge from '../components/serviceos/VersionBadge.vue'
import WorkOrderTimeline from '../components/serviceos/WorkOrderTimeline.vue'
import type { ProjectStageBarItem, RiskPanelItem } from '../components/serviceos/types'
import StatusPill from '../components/StatusPill.vue'
import TaskActionButtons from '../components/TaskActionButtons.vue'
import TimelineAuditSection from '../components/TimelineAuditSection.vue'
import WorkOrderOverviewFacts from '../components/WorkOrderOverviewFacts.vue'
import {
  formatDateTime,
  stageLabel,
  taskLabel,
  timelineEventLabel,
} from '../presenters/work-order'

const route = useRoute()
const workOrderId = computed(() => String(route.params.id))
const activeTab = ref('FORMS_EVIDENCE')
const drawerOpen = ref(false)

const workspace = useQuery({
  queryKey: computed(() => ['work-order-workspace', workOrderId.value]),
  queryFn: () => loadAdminWorkOrderWorkspace(workOrderId.value),
})
const tasks = useQuery({
  queryKey: computed(() => ['work-order-workspace', workOrderId.value, 'TASKS']),
  queryFn: () => loadWorkspaceSection(workOrderId.value, 'TASKS'),
})
const section = useQuery({
  queryKey: computed(() => ['work-order-workspace', workOrderId.value, activeTab.value]),
  queryFn: () => loadWorkspaceSection(workOrderId.value, activeTab.value),
  enabled: computed(() => activeTab.value !== 'overview' && activeTab.value !== 'TASKS'),
})
const timelineSection = useQuery({
  queryKey: computed(() => ['work-order-workspace', workOrderId.value, 'TIMELINE_AUDIT']),
  queryFn: () => loadWorkspaceSection(workOrderId.value, 'TIMELINE_AUDIT'),
})

const currentTask = computed(() => workspace.data.value?.workspace.currentTaskSummary ?? null)
const terminalWorkOrder = computed(() => ['FULFILLED', 'CANCELLED', 'CLOSED'].includes(workspace.data.value?.workspace.header.status ?? ''))
const projectPersonnel = computed(() => workspace.data.value?.workspace.projectPersonnel ?? [])

const progressStages = computed<ProjectStageBarItem[]>(() => (
  (workspace.data.value?.workspace.workflowStages ?? []).map((stage) => ({
    code: stage.stageCode,
    label: stage.stageCode === workspace.data.value?.workspace.header.currentStageCode
      ? (workspace.data.value?.stageName ?? safeStageLabel(stage.stageCode))
      : safeStageLabel(stage.stageCode),
    status: stage.status === 'COMPLETED' || stage.status === 'SKIPPED'
      ? 'completed'
      : stage.status === 'ACTIVE'
        ? 'current'
        : stage.status === 'BLOCKED'
          ? 'blocked'
          : 'pending',
    detail: stage.status === 'ACTIVE' ? '当前进行中' : stage.status === 'COMPLETED' ? formatDateTime(stage.completedAt) : undefined,
  }))
))

const riskItems = computed<RiskPanelItem[]>(() => {
  const data = workspace.data.value
  if (!data) return []
  const items: RiskPanelItem[] = []
  const sla = data.workspace.slaSummary
  const exceptions = data.workspace.exceptionSummary
  if ((sla?.breachedCount ?? 0) > 0) {
    items.push({ key: 'sla-breached', label: 'SLA 已超时', count: sla?.breachedCount ?? null, description: '需要进入工单动作区处置', tone: 'danger' })
  } else if ((sla?.openCount ?? 0) > 0) {
    items.push({ key: 'sla-open', label: 'SLA 计时中', count: sla?.openCount ?? null, description: '关注当前阶段剩余时效', tone: 'warning' })
  }
  if ((exceptions?.openCount ?? 0) > 0) {
    items.push({ key: 'exceptions', label: '未关闭异常', count: exceptions?.openCount ?? null, description: '进入异常事实和处置记录', tone: 'warning' })
  }
  if (data.blockedActions.length) {
    items.push({ key: 'blocked-actions', label: '受限操作', count: data.blockedActions.length, description: '服务端返回当前不可执行原因', tone: 'neutral' })
  }
  return items
})

function responsibilityDisplay(value: string | null | undefined, pending: string): string {
  if (value) return value
  return terminalWorkOrder.value ? '已释放' : pending
}

function assigneeDisplay(value: string | null | undefined): string {
  if (value) return value
  return terminalWorkOrder.value ? '已结束' : '待确认'
}

function taskStatusLabel(status: string | null | undefined) {
  if (status === 'READY') return '待领取'
  if (status === 'CLAIMED') return '已领取'
  if (status === 'RUNNING') return '执行中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'CANCELLED') return '已取消'
  return status ? '任务状态待确认' : '等待流程事件'
}

function safeStageLabel(code: string | null) {
  try {
    return stageLabel(code)
  } catch {
    return '阶段名称缺失'
  }
}

function taskTitle(task: WorkOrderWorkspaceTask | null): string {
  try {
    return task ? taskLabel(task.taskType) : (workspace.data.value?.taskName ?? '暂无进行中的任务')
  } catch {
    return workspace.data.value?.taskName ?? '当前任务名称待确认'
  }
}

function safeTaskLabel(code: string | null): string {
  try {
    return taskLabel(code)
  } catch {
    return code === currentTask.value?.taskType
      ? (workspace.data.value?.taskName ?? '当前任务名称待确认')
      : '当前任务名称待确认'
  }
}

function copyOrderCode() {
  const value = workspace.data.value?.workspace.header.externalOrderCode
  if (value) void globalThis.navigator.clipboard.writeText(value)
}
</script>

<template>
  <Page
    title="履约过程工作区"
    description="从阶段、责任、当前任务、SLA 和业务事件判断一张工单下一步。"
    content-class="serviceos-workspace-content"
  >
    <template v-if="workspace.data.value" #extra>
      <Space wrap>
        <TaskActionButtons
          :actions="workspace.data.value.allowedActions"
          :task="currentTask"
          :service-assignment-summary="workspace.data.value.workspace.serviceAssignmentSummary"
          @manage-assignment="drawerOpen = true"
        />
      </Space>
    </template>

    <PageError v-if="workspace.isError.value" :detail="workspace.error.value?.message ?? '工单工作区加载失败'" />
    <div v-else-if="workspace.isLoading.value" class="page-loading">正在加载履约过程…</div>
    <template v-else-if="workspace.data.value">
      <PageError v-if="!workspace.data.value.dataComplete" :detail="workspace.data.value.dataProblem ?? '工单工作区数据不完整'" />
      <template v-else>
        <section class="workorder-ops-header">
          <div class="workorder-ops-header__title">
            <div class="workorder-code-line"><h1>{{ workspace.data.value.workspace.header.externalOrderCode }}</h1><button type="button" class="icon-link" aria-label="复制工单编号" @click="copyOrderCode"><CopyOutlined /></button><StatusPill tone="green" :label="workspace.data.value.statusName ?? '状态待确认'" /></div>
            <p>{{ workspace.data.value.clientName ?? '客户品牌待确认' }} · {{ workspace.data.value.projectName ?? '项目待确认' }} · {{ workspace.data.value.serviceName ?? '服务产品待确认' }}</p>
          </div>
          <div class="workorder-ops-header__meta"><span>最新事实</span><strong>{{ formatDateTime(workspace.data.value.workspace.meta.asOf) }}</strong><VersionBadge status="ACTIVE" :version="workspace.data.value.workspace.header.configurationBundleVersion.replace(/^V/, '')" /></div>
        </section>

        <section class="workorder-summary-grid" aria-label="工单摘要">
          <div><span>当前阶段</span><strong>{{ workspace.data.value.stageName ?? '阶段待确认' }}</strong><small>{{ workspace.data.value.workspace.header.currentStageCode ? '流程运行已定位' : '等待流程阶段' }}</small></div>
          <div><span>当前任务</span><strong>{{ workspace.data.value.taskName ?? '暂无进行中任务' }}</strong><small>{{ currentTask ? taskStatusLabel(currentTask.status) : '等待流程事件' }}</small></div>
          <div><span>责任网点</span><strong>{{ responsibilityDisplay(workspace.data.value.workspace.header.currentNetworkDisplayName, '待分配') }}</strong><small>履约组织责任</small></div>
          <div><span>责任师傅</span><strong>{{ responsibilityDisplay(workspace.data.value.workspace.header.currentTechnicianDisplayName, '待分配') }}</strong><small>现场执行责任</small></div>
          <div><span>SLA 状态</span><strong>{{ workspace.data.value.workspace.slaSummary?.breachedCount ? '存在超时' : workspace.data.value.workspace.slaSummary ? '计时中' : '暂不可见' }}</strong><small>{{ workspace.data.value.workspace.slaSummary ? `${workspace.data.value.workspace.slaSummary.openCount} 项运行中` : '服务端未返回' }}</small></div>
          <div><span>项目协同</span><strong>{{ assigneeDisplay(workspace.data.value.workspace.header.currentAssigneeDisplayName) }}</strong><small>当前任务处理人</small></div>
        </section>

        <FulfillmentStageBar :stages="progressStages" title="履约阶段进度" description="完成、当前和未开始状态来自工单绑定的履约流程。" />

        <section class="workorder-ops-grid">
          <main class="workorder-ops-main">
            <WorkOrderTimeline
              :items="timelineSection.data.value?.timeline?.items ?? (timelineSection.isError.value ? null : undefined)"
              :freshness-status="timelineSection.data.value?.timeline?.freshnessStatus ?? workspace.data.value.workspace.timelineFreshnessStatus"
              title="业务时间线"
              description="把联系、预约、上门、审核、回传和责任变化放在同一条履约事实流中。"
            />

            <TaskCard
              :task="currentTask"
              :title="taskTitle(currentTask)"
              :stage-name="workspace.data.value.stageName"
              :status-label="taskStatusLabel(currentTask?.status)"
              :network-name="workspace.data.value.workspace.header.currentNetworkDisplayName"
              :technician-name="workspace.data.value.workspace.header.currentTechnicianDisplayName"
              :address="workspace.data.value.workspace.maskedServiceAddress"
              :terminal="terminalWorkOrder"
            >
              <template #status><StatusPill :tone="terminalWorkOrder ? 'gray' : currentTask ? 'blue' : 'orange'" :label="terminalWorkOrder ? '已结束' : currentTask ? taskStatusLabel(currentTask.status) : '等待流程事件'" /></template>
              <template #actions>
                <TaskActionButtons
                  :actions="workspace.data.value.allowedActions"
                  :task="currentTask"
                  :service-assignment-summary="workspace.data.value.workspace.serviceAssignmentSummary"
                  @manage-assignment="drawerOpen = true"
                />
                <p v-if="!workspace.data.value.allowedActions.length">当前没有可执行操作</p>
              </template>
            </TaskCard>

            <Card class="workspace-tabs-panel" :bordered="false">
              <Tabs v-model:active-key="activeTab">
                <Tabs.TabPane v-for="tab in [{ k:'overview',l:'工单事实' }, { k:'TASKS',l:'任务记录' }, { k:'APPOINTMENTS_VISITS',l:'预约与上门' }, { k:'FORMS_EVIDENCE',l:'表单资料' }, { k:'REVIEWS_CORRECTIONS',l:'审核整改' }, { k:'INTEGRATION',l:'外部回传' }, { k:'TIMELINE_AUDIT',l:'操作日志' }]" :key="tab.k" :tab="tab.l" />
              </Tabs>
              <div class="tab-content">
                <WorkOrderOverviewFacts v-if="activeTab === 'overview'" :view="workspace.data.value" />
                <template v-else-if="activeTab === 'TASKS'">
                  <div v-for="item in tasks.data.value?.tasks?.items ?? []" :key="item.taskId" class="record-row"><strong>{{ safeTaskLabel(item.taskType) }}</strong><span>{{ taskStatusLabel(item.status) }}</span></div>
                  <p v-if="!tasks.data.value?.tasks?.items?.length" class="section-state"><ClockCircleOutlined /><strong>暂无任务记录</strong></p>
                </template>
                <PageError v-else-if="section.isError.value" :detail="section.error.value?.message ?? '业务区块加载失败'" />
                <div v-else-if="section.isLoading.value" class="section-state"><ClockCircleOutlined /><strong>正在加载业务区块</strong><p>页面仅展示服务端返回的正式业务数据。</p></div>
                <AppointmentsVisitsSection v-else-if="activeTab === 'APPOINTMENTS_VISITS'" :data="section.data.value?.appointmentsVisits ?? null" />
                <FormsEvidenceSection v-else-if="activeTab === 'FORMS_EVIDENCE'" :data="section.data.value?.formsEvidence ?? null" />
                <ReviewsCorrectionsSection v-else-if="activeTab === 'REVIEWS_CORRECTIONS'" :data="section.data.value?.reviewsCorrections ?? null" @submitted="activeTab = 'INTEGRATION'" />
                <IntegrationSection v-else-if="activeTab === 'INTEGRATION'" :data="section.data.value?.integration ?? null" />
                <TimelineAuditSection v-else-if="activeTab === 'TIMELINE_AUDIT'" :data="section.data.value?.timeline ?? null" />
              </div>
            </Card>
          </main>

          <aside class="workorder-ops-rail">
            <SlaHealthCard
              :risk-count="workspace.data.value.workspace.slaSummary?.openCount"
              :breached-count="workspace.data.value.workspace.slaSummary?.breachedCount"
              title="SLA 健康度"
              description="当前工单时效事实"
            />
            <RiskPanel :items="riskItems" title="风险与提醒" description="允许动作与风险由服务端事实决定。" />
            <section class="workorder-context-panel">
              <header><span class="sos-eyebrow">CONTEXT</span><h2>履约上下文</h2></header>
              <dl><div><dt>客户</dt><dd>{{ workspace.data.value.workspace.maskedCustomerName ?? '受限或未提供' }}</dd></div><div><dt>设备 / 服务</dt><dd>{{ workspace.data.value.serviceName ?? '设备信息未返回' }}</dd></div><div><dt>项目</dt><dd>{{ workspace.data.value.projectName ?? '项目待确认' }}</dd></div><div><dt>方案版本</dt><dd>{{ workspace.data.value.workspace.header.configurationBundleVersion }}</dd></div><div><dt>异常</dt><dd>{{ workspace.data.value.workspace.exceptionSummary?.openCount ?? '暂不可见' }} 项</dd></div></dl>
            </section>
            <section class="workorder-responsibility-panel">
              <header><span class="sos-eyebrow">RESPONSIBILITY CHAIN</span><h2>责任链</h2></header>
              <div class="responsibility-chain">
                <template v-for="person in projectPersonnel" :key="person.positionCode"><div><span>{{ person.positionName }}</span><strong :class="{ 'responsibility-missing': person.matchStatus !== 'ASSIGNED' }">{{ person.displayName ?? '项目人员待确认' }}</strong><small v-if="person.matchedRegionName">{{ person.inherited ? `继承 ${person.matchedRegionName}` : `命中 ${person.matchedRegionName}` }}</small></div></template>
                <div><span>当前任务处理人</span><strong>{{ assigneeDisplay(workspace.data.value.workspace.header.currentAssigneeDisplayName) }}</strong></div>
                <div><span>责任网点</span><strong>{{ responsibilityDisplay(workspace.data.value.workspace.header.currentNetworkDisplayName, '待分配') }}</strong></div>
                <div><span>责任师傅</span><strong>{{ responsibilityDisplay(workspace.data.value.workspace.header.currentTechnicianDisplayName, '待分配') }}</strong></div>
              </div>
            </section>
            <section class="workorder-recent-panel">
              <header><span class="sos-eyebrow">LATEST SIGNAL</span><h2>最近时间线</h2></header>
              <ol><li v-for="item in timelineSection.data.value?.timeline?.items.slice(0, 4) ?? []" :key="item.id"><span /><div><strong>{{ timelineEventLabel(item.eventType) }}</strong><small>{{ formatDateTime(item.occurredAt) }}</small></div></li><li v-if="!timelineSection.data.value?.timeline?.items.length"><span /><div><strong>等待业务事件投影</strong><small>{{ formatDateTime(workspace.data.value.workspace.meta.asOf) }}</small></div></li></ol>
            </section>
          </aside>
        </section>

        <AssignmentDrawer :open="drawerOpen" :task-id="currentTask?.taskId ?? null" @close="drawerOpen = false" />
      </template>
    </template>
  </Page>
</template>
