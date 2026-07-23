<script setup lang="ts">
import type { WorkOrderWorkspaceTask } from '@serviceos/api-client'
import { loadAdminWorkOrderWorkspace, loadWorkspaceSection } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import {
  Card,
  CheckOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  Space,
  Tabs,
  ToolOutlined,
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

// 右侧栏「最近时间线」独立加载；与 TIMELINE_AUDIT tab 共用同一 queryKey，自动去重。
const timelineSection = useQuery({
  queryKey: computed(() => ['work-order-workspace', workOrderId.value, 'TIMELINE_AUDIT']),
  queryFn: () => loadWorkspaceSection(workOrderId.value, 'TIMELINE_AUDIT'),
})
const railTimeline = computed(() => timelineSection.data.value?.timeline?.items.slice(0, 3) ?? [])

const currentTask = computed(() => workspace.data.value?.workspace.currentTaskSummary ?? null)
// 同时兼容新版 SURVEY/INSTALLATION 与历史 PILOT_* 阶段码。
const stageOrder = [
  'PILOT_INTAKE',
  'PILOT_DISPATCH',
  'PILOT_APPOINTMENT',
  'SURVEY',
  'PILOT_SURVEY',
  'INSTALLATION',
  'PILOT_INSTALLATION',
  'FINAL_REVIEW',
  'CLIENT_CALLBACK',
]
const activeStageIndex = computed(() => Math.max(0, stageOrder.indexOf(currentTask.value?.stageCode ?? '')))
const projectPersonnel = computed(() => workspace.data.value?.workspace.projectPersonnel ?? [])

function taskTitle(task: WorkOrderWorkspaceTask | null): string {
  return task ? taskLabel(task.taskType) : '暂无进行中的任务'
}

function copyOrderCode() {
  const value = workspace.data.value?.workspace.header.externalOrderCode
  if (value) void globalThis.navigator.clipboard.writeText(value)
}
</script>

<template>
  <Page
    title="工单详情"
    description="查看工单当前状态、履约进度、责任人、任务、资料、审核与外部回传信息。"
    content-class="serviceos-workspace-content"
  >
    <template
      v-if="workspace.data.value"
      #extra
    >
      <Space wrap>
        <TaskActionButtons
          :actions="workspace.data.value.allowedActions"
          :task="currentTask"
          :service-assignment-summary="workspace.data.value.workspace.serviceAssignmentSummary"
          @manage-assignment="drawerOpen = true"
        />
      </Space>
    </template>

    <PageError
      v-if="workspace.isError.value"
      :detail="workspace.error.value?.message ?? '工单工作区加载失败'"
    />
    <div
      v-else-if="workspace.isLoading.value"
      class="page-loading"
    >
      正在加载工单工作区…
    </div>
    <template v-else-if="workspace.data.value">
      <PageError
        v-if="!workspace.data.value.dataComplete"
        :detail="workspace.data.value.dataProblem ?? '页面数据不完整'"
      />
      <section
        v-else
        class="summary-strip"
      >
        <div class="summary-cell customer-summary">
          <span>工单编号</span>
          <div class="order-code-line">
            <strong>{{ workspace.data.value.workspace.header.externalOrderCode }}</strong>
            <button
              type="button"
              class="icon-link"
              aria-label="复制工单编号"
              @click="copyOrderCode"
            >
              <CopyOutlined />
            </button>
          </div>
          <div class="customer-contact-line">
            <span><small>客户姓名</small><b>{{ workspace.data.value.workspace.maskedCustomerName ?? '未提供' }}</b></span>
            <span><small>联系方式</small><b>{{ workspace.data.value.workspace.maskedCustomerPhone ?? '未提供' }}</b></span>
          </div>
        </div>
        <div class="summary-cell">
          <span>项目</span><strong>{{ workspace.data.value.projectName }}</strong><small>服务产品</small><b>{{ workspace.data.value.serviceName }}</b>
        </div>
        <div class="summary-cell">
          <span>当前状态</span><StatusPill
            tone="green"
            :label="workspace.data.value.statusName ?? '数据不完整'"
          /><small>当前阶段</small><b>{{ workspace.data.value.stageName }}</b>
        </div>
        <div class="summary-cell">
          <span>当前任务</span><strong>{{ workspace.data.value.taskName }}</strong><small>客户品牌</small><b>{{ workspace.data.value.clientName }}</b>
        </div>
        <div class="summary-cell">
          <span>SLA 剩余时间</span><strong class="sla-value">{{ workspace.data.value.workspace.slaSummary?.breachedCount ? '已经超时' : `进行中 ${workspace.data.value.workspace.slaSummary?.openCount ?? 0} 项` }}</strong><small>风险状态</small><b>{{ workspace.data.value.workspace.exceptionSummary?.openCount ? `${workspace.data.value.workspace.exceptionSummary.openCount} 项异常` : '暂无异常' }}</b>
        </div>
        <div class="summary-cell">
          <span>当前责任人</span><strong>{{ workspace.data.value.workspace.header.currentAssigneeDisplayName ?? '待分配' }}</strong><small>配置版本</small><b>{{ workspace.data.value.workspace.header.configurationBundleVersion }}</b>
        </div>
      </section>

      <div class="workspace-grid">
        <div class="workspace-primary">
          <Card
            class="progress-panel"
            :bordered="false"
          >
            <h2>履约进度</h2>
            <div class="progress-steps">
              <div
                v-for="(stage, index) in stageOrder"
                :key="stage"
                class="progress-step"
                :class="{ done: index < activeStageIndex, current: index === activeStageIndex }"
              >
                <span class="step-dot"><CheckOutlined v-if="index < activeStageIndex" /><template v-else>{{ index + 1 }}</template></span>
                <strong>{{ stageLabel(stage) }}</strong>
                <small>{{ index < activeStageIndex ? '已完成' : index === activeStageIndex ? '进行中' : '待进行' }}</small>
              </div>
            </div>
          </Card>

          <Card
            class="current-task-card"
            :bordered="false"
          >
            <div class="current-task-panel">
              <div class="task-main">
                <h2>当前任务</h2>
                <div class="task-title">
                  <span class="task-icon"><ToolOutlined /></span><div><strong>{{ taskTitle(currentTask) }}</strong><p>{{ stageLabel(currentTask?.stageCode ?? null) }} · {{ currentTask?.status ? '待处理' : '未开始' }}</p></div>
                </div>
                <dl class="task-facts">
                  <div><dt>责任网点</dt><dd>{{ workspace.data.value.workspace.header.currentNetworkDisplayName ?? '待分配' }}</dd></div>
                  <div><dt>责任师傅</dt><dd>{{ workspace.data.value.workspace.header.currentTechnicianDisplayName ?? '待分配' }}</dd></div>
                  <div><dt>预约信息</dt><dd>在“预约与上门”中查看</dd></div>
                  <div><dt>客户地址</dt><dd>{{ workspace.data.value.workspace.maskedServiceAddress ?? '未提供' }}</dd></div>
                </dl>
              </div>
              <aside class="task-actions">
                <span>允许操作</span><TaskActionButtons
                  :actions="workspace.data.value.allowedActions"
                  :task="currentTask"
                  :service-assignment-summary="workspace.data.value.workspace.serviceAssignmentSummary"
                  @manage-assignment="drawerOpen = true"
                /><p v-if="!workspace.data.value.allowedActions.length">
                  当前没有可执行操作
                </p>
              </aside>
            </div>
          </Card>

          <Card
            class="workspace-tabs-panel"
            :bordered="false"
          >
            <Tabs v-model:active-key="activeTab">
              <Tabs.TabPane
                v-for="tab in [{k:'overview',l:'基本信息'},{k:'TASKS',l:'任务记录'},{k:'APPOINTMENTS_VISITS',l:'预约与上门'},{k:'FORMS_EVIDENCE',l:'表单资料'},{k:'REVIEWS_CORRECTIONS',l:'审核整改'},{k:'INTEGRATION',l:'外部回传'},{k:'TIMELINE_AUDIT',l:'操作日志'}]"
                :key="tab.k"
                :tab="tab.l"
              />
            </Tabs>
            <div class="tab-content">
              <WorkOrderOverviewFacts
                v-if="activeTab === 'overview'"
                :view="workspace.data.value"
              />
              <template v-else-if="activeTab === 'TASKS'">
                <div
                  v-for="item in tasks.data.value?.tasks?.items ?? []"
                  :key="item.taskId"
                  class="record-row"
                >
                  <strong>{{ taskLabel(item.taskType) }}</strong><span>{{ item.status }}</span>
                </div>
                <p
                  v-if="!tasks.data.value?.tasks?.items?.length"
                  class="section-state"
                >
                  <ClockCircleOutlined /><strong>暂无任务记录</strong>
                </p>
              </template>
              <PageError
                v-else-if="section.isError.value"
                :detail="section.error.value?.message ?? '业务区块加载失败'"
              />
              <div
                v-else-if="section.isLoading.value"
                class="section-state"
              >
                <ClockCircleOutlined /><strong>正在加载业务区块</strong><p>页面仅展示服务端返回的正式业务数据。</p>
              </div>
              <AppointmentsVisitsSection
                v-else-if="activeTab === 'APPOINTMENTS_VISITS'"
                :data="section.data.value?.appointmentsVisits ?? null"
              />
              <FormsEvidenceSection
                v-else-if="activeTab === 'FORMS_EVIDENCE'"
                :data="section.data.value?.formsEvidence ?? null"
              />
              <ReviewsCorrectionsSection
                v-else-if="activeTab === 'REVIEWS_CORRECTIONS'"
                :data="section.data.value?.reviewsCorrections ?? null"
              />
              <IntegrationSection
                v-else-if="activeTab === 'INTEGRATION'"
                :data="section.data.value?.integration ?? null"
              />
              <TimelineAuditSection
                v-else-if="activeTab === 'TIMELINE_AUDIT'"
                :data="section.data.value?.timeline ?? null"
              />
            </div>
          </Card>
        </div>

        <aside class="context-rail">
          <section>
            <h2>风险与提醒</h2><dl>
              <div>
                <dt>SLA 状态</dt><dd class="orange">
                  {{ workspace.data.value.workspace.slaSummary?.breachedCount ? '已超时' : '计时中' }}
                </dd>
              </div><div>
                <dt>逾期风险</dt><dd class="green">
                  无
                </dd>
              </div><div>
                <dt>不可执行操作</dt><dd class="orange">
                  {{ workspace.data.value.blockedActions.length }} 项
                </dd>
              </div>
            </dl>
          </section>
          <section>
            <h2>当前责任链</h2><div class="responsibility">
              <template
                v-for="person in projectPersonnel"
                :key="person.positionCode"
              >
                <b>{{ person.positionName }}</b>
                <strong :class="{ 'responsibility-missing': person.matchStatus !== 'ASSIGNED' }">
                  {{ person.displayName ?? '项目人员待确认' }}
                  <small v-if="person.matchedRegionName">
                    {{ person.inherited ? `继承 ${person.matchedRegionName}` : `命中 ${person.matchedRegionName}` }}
                  </small>
                </strong>
              </template>
              <b>当前任务处理人</b><strong>{{ workspace.data.value.workspace.header.currentAssigneeDisplayName ?? '待确认' }}</strong><b>责任网点</b><strong>{{ workspace.data.value.workspace.header.currentNetworkDisplayName ?? '待分配' }}</strong><b>责任师傅</b><strong>{{ workspace.data.value.workspace.header.currentTechnicianDisplayName ?? '待分配' }}</strong>
            </div>
          </section>
          <section><h2>外部集成信息</h2><dl><div><dt>来源系统</dt><dd>{{ workspace.data.value.clientName }}</dd></div><div><dt>配置版本</dt><dd>{{ workspace.data.value.workspace.header.configurationBundleVersion }}</dd></div></dl></section>
          <section>
            <h2>最近时间线</h2><ol class="timeline">
              <template v-if="railTimeline.length">
                <li
                  v-for="item in railTimeline"
                  :key="item.id"
                >
                  <span /><div><strong>{{ timelineEventLabel(item.eventType) }}</strong><small>{{ formatDateTime(item.occurredAt) }}</small></div>
                </li>
              </template>
              <template v-else>
                <li><span /><div><strong>工作区数据已更新</strong><small>{{ formatDateTime(workspace.data.value.workspace.meta.asOf) }}</small></div></li><li><span /><div><strong>{{ workspace.data.value.taskName }}</strong><small>当前任务</small></div></li>
              </template>
            </ol>
          </section>
        </aside>
      </div>

      <AssignmentDrawer
        :open="drawerOpen"
        :task-id="currentTask?.taskId ?? null"
        @close="drawerOpen = false"
      />
    </template>
  </Page>
</template>
