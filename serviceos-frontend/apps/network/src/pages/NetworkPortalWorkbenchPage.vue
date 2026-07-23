<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getNetworkPortalWorkbench,
  listNetworkPortalAssignCandidates,
  listNetworkPortalTasks,
  type NetworkPortalAssignCandidateItem,
  type NetworkPortalTaskItem,
  type NetworkPortalWorkbench,
} from '../api/networkPortal'
import { formatDateTime, safeProblemMessage } from '../product/labels'
import PageState from '../components/PageState.vue'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import AssignTechnicianDrawer from '../components/AssignTechnicianDrawer.vue'
import { statusLabel } from '../product/labels'

const props = defineProps<{ networkContextId: string | null }>()
const data = ref<NetworkPortalWorkbench | null>(null)
const unassignedTasks = ref<NetworkPortalTaskItem[]>([])
const assignCandidates = ref<NetworkPortalAssignCandidateItem[]>([])
const workOrderRegionSummary = ref<string | null>(null)
const rankingExplanation = ref<string | null>(null)
const assignEmptyReason = ref<string | null>(null)
const loading = ref(false)
const loadingCandidates = ref(false)
const error = ref<string | null>(null)
const tasksError = ref<string | null>(null)
const candidatesError = ref<string | null>(null)
const drawerOpen = ref(false)
const assignTask = ref<NetworkPortalTaskItem | null>(null)

const summaryItems = computed<SummaryStripItem[]>(() => {
  if (!data.value) return []
  const slaOpen = data.value.slaSummary?.openCount
  const slaBreached = data.value.slaSummary?.breachedCount ?? 0
  return [
    {
      key: 'unassigned',
      label: '待分配工单',
      value: data.value.unassignedTechnicianTaskCount ?? 0,
      hint: '下一步：选择师傅',
      to: '/network-portal/tasks',
      testId: 'workbench-unassigned-count',
      tone: (data.value.unassignedTechnicianTaskCount ?? 0) > 0 ? 'warning' : 'default',
    },
    {
      key: 'today-appointments',
      label: '今日预约',
      value:
        typeof data.value.todayAppointmentCount === 'number'
          ? data.value.todayAppointmentCount
          : '无权限',
      hint:
        typeof data.value.todayAppointmentCount === 'number'
          ? '运营日 Asia/Shanghai'
          : '缺少 networkPortal.manageAppointment',
      to:
        typeof data.value.todayAppointmentCount === 'number'
          ? '/network-portal/appointments'
          : undefined,
      testId: 'workbench-today-appointment-count',
      tone:
        typeof data.value.todayAppointmentCount === 'number' &&
        data.value.todayAppointmentCount > 0
          ? 'warning'
          : 'default',
    },
    {
      key: 'active-wo',
      label: '进行中工单',
      value: data.value.activeWorkOrderCount,
      to: '/network-portal/work-orders',
      testId: 'workbench-active-work-orders',
    },
    {
      key: 'active-tasks',
      label: '进行中任务',
      value: data.value.activeTaskCount,
      to: '/network-portal/tasks',
      testId: 'workbench-active-tasks',
    },
    {
      key: 'sla',
      label: '即将超时 / 已超时',
      value:
        typeof slaOpen === 'number' ? `${slaOpen} / ${slaBreached}` : '无权限',
      hint: typeof slaOpen === 'number' ? '优先处理已超时' : '缺少 sla.read',
      testId: 'workbench-sla-summary',
      tone: slaBreached > 0 ? 'critical' : 'default',
    },
    {
      key: 'corrections',
      label: '待整改',
      value: data.value.openCorrectionCaseCount ?? '—',
      to:
        typeof data.value.openCorrectionCaseCount === 'number'
          ? '/network-portal/corrections'
          : undefined,
      testId: 'workbench-correction-count',
    },
    {
      key: 'exceptions',
      label: '异常待处理',
      value: data.value.openOperationalExceptionCount ?? '—',
      to:
        typeof data.value.openOperationalExceptionCount === 'number'
          ? '/network-portal/exceptions'
          : undefined,
      testId: 'workbench-exception-count',
    },
    {
      key: 'technicians',
      label: '可接单师傅',
      value: data.value.activeTechnicianCount,
      to: '/network-portal/technicians',
      testId: 'workbench-active-technicians',
    },
  ]
})

async function load() {
  if (!props.networkContextId) {
    data.value = null
    unassignedTasks.value = []
    assignCandidates.value = []
    workOrderRegionSummary.value = null
    rankingExplanation.value = null
    assignEmptyReason.value = null
    error.value = '请选择网点上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    data.value = await getNetworkPortalWorkbench(props.networkContextId)
    error.value = null
  } catch (err) {
    data.value = null
    error.value = safeProblemMessage(err)
  } finally {
    loading.value = false
  }
  await loadUnassignedTasks()
}

async function loadUnassignedTasks() {
  if (!props.networkContextId) return
  try {
    const page = await listNetworkPortalTasks(props.networkContextId)
    // 服务端 ACTIVE 任务列表中 technicianId 为空 = 待指派；与 workbench 计数同源字段。
    unassignedTasks.value = page.items.filter((item) => !item.technicianId)
    tasksError.value = null
  } catch (err) {
    unassignedTasks.value = []
    tasksError.value = safeProblemMessage(err)
  }
}

async function loadAssignCandidates(taskId: string) {
  if (!props.networkContextId) return
  loadingCandidates.value = true
  try {
    const page = await listNetworkPortalAssignCandidates(props.networkContextId, taskId)
    assignCandidates.value = page.items
    workOrderRegionSummary.value = page.workOrderRegionSummary
    rankingExplanation.value = page.rankingExplanation
    assignEmptyReason.value = page.emptyReason ?? null
    candidatesError.value = null
  } catch (err) {
    assignCandidates.value = []
    workOrderRegionSummary.value = null
    rankingExplanation.value = null
    assignEmptyReason.value = null
    candidatesError.value = safeProblemMessage(err)
  } finally {
    loadingCandidates.value = false
  }
}

function openAssign(task: NetworkPortalTaskItem) {
  assignTask.value = task
  drawerOpen.value = true
  void loadAssignCandidates(task.taskId)
}

async function onAssigned() {
  await load()
}

onMounted(() => {
  void load()
})
watch(
  () => props.networkContextId,
  () => {
    void load()
  },
)
</script>

<template>
  <section data-testid="network-portal-workbench" class="workbench">
    <header class="hero">
      <div>
        <p class="eyebrow">网点协作</p>
        <h2>本网点工作台</h2>
        <p class="subtitle">今天要处理什么、由谁负责、何时完成、有什么风险。</p>
      </div>
      <button type="button" data-testid="network-workbench-reload" @click="load">刷新</button>
    </header>

    <PageState v-if="loading" kind="loading" />
    <PageState v-else-if="error" kind="error" :description="error" @reload="load" />
    <template v-else-if="data">
      <SummaryStrip
        :items="summaryItems"
        :as-of="formatDateTime(data.asOf)"
      />

      <section class="panel" data-testid="workbench-today-timeline">
        <header class="panel__head">
          <h3>今日任务时间轴</h3>
          <span class="muted">运营日 Asia/Shanghai</span>
        </header>
        <ol v-if="data.todayTimeline?.length" class="timeline">
          <li
            v-for="bucket in data.todayTimeline"
            :key="bucket.bucketCode"
            :data-testid="`timeline-bucket-${bucket.bucketCode}`"
            :class="{ active: bucket.count > 0 }"
          >
            <strong>{{ bucket.label }}</strong>
            <span class="count">{{ bucket.count }}</span>
            <span class="muted">{{ bucket.summary }}</span>
          </li>
        </ol>
        <PageState
          v-else
          kind="empty"
          guide="暂无运营节奏摘要。"
        />
      </section>

      <div class="workbench-grid">
        <section class="panel" data-testid="workbench-unassigned-table">
          <header class="panel__head">
            <h3>待分配工单</h3>
            <RouterLink to="/network-portal/tasks">全部任务</RouterLink>
          </header>
          <p v-if="tasksError" class="error">{{ tasksError }}</p>
          <table v-else-if="unassignedTasks.length">
            <thead>
              <tr>
                <th>工单</th>
                <th>服务类型</th>
                <th>当前任务</th>
                <th>阶段</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in unassignedTasks" :key="task.taskId">
                <td>
                  <RouterLink
                    :to="`/network-portal/work-orders/${task.workOrderId}`"
                    :data-testid="`unassigned-wo-${task.workOrderId}`"
                  >
                    打开工作区
                  </RouterLink>
                </td>
                <td>{{ statusLabel(task.serviceProductCode || task.businessType || '') || '—' }}</td>
                <td>{{ statusLabel(task.taskType || '') || '—' }}</td>
                <td>{{ statusLabel(task.stageCode || '') || '—' }}</td>
                <td>
                  <button
                    type="button"
                    class="primary"
                    :data-testid="`assign-open-${task.taskId}`"
                    @click="openAssign(task)"
                  >
                    分配师傅
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <PageState
            v-else
            kind="empty"
            guide="当前没有待指派师傅的任务。新工单到达或网点接单后将出现在此。"
          />
        </section>

        <section class="panel" data-testid="network-workbench-capacity">
          <header class="panel__head">
            <h3>
              <RouterLink to="/network-portal/capacity" data-testid="workbench-capacity-deeplink">
                师傅状态与负载
              </RouterLink>
            </h3>
          </header>
          <ul v-if="data.capacity.length" class="capacity-list">
            <li
              v-for="row in data.capacity"
              :key="row.capacityCounterId"
              :data-testid="`workbench-capacity-${row.businessType}`"
            >
              <strong>{{ row.businessType ? statusLabel(row.businessType) : '—' }}</strong>
              <span>
                占用 {{ row.occupiedUnits }} / 上限 {{ row.maxUnits }}（可用 {{ row.availableUnits }}）
              </span>
              <span class="muted" data-testid="workbench-capacity-updated-at">
                更新 {{ formatDateTime(row.updatedAt) }}
              </span>
            </li>
          </ul>
          <PageState
            v-else
            kind="empty"
            guide="暂无容量计数。完成网点容量配置后将在此显示师傅负载。"
          />
        </section>

        <section class="panel" data-testid="workbench-today-appointments">
          <header class="panel__head">
            <h3>今日预约</h3>
            <span class="muted">不含客户地址等敏感字段</span>
          </header>
          <PageState
            v-if="typeof data.todayAppointmentCount !== 'number'"
            kind="forbidden"
            description="缺少 networkPortal.manageAppointment，无法加载今日预约。"
          />
          <table v-else-if="data.todayAppointments?.length">
            <thead>
              <tr>
                <th>时间窗口</th>
                <th>状态</th>
                <th>类型</th>
                <th>师傅</th>
                <th>工单</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in data.todayAppointments"
                :key="item.appointmentId"
                :data-testid="`today-appointment-${item.appointmentId}`"
              >
                <td>
                  {{ item.windowStart ? formatDateTime(item.windowStart) : '—' }}
                  <span class="muted">～</span>
                  {{ item.windowEnd ? formatDateTime(item.windowEnd) : '—' }}
                </td>
                <td>{{ statusLabel(item.status) || item.status }}</td>
                <td>{{ statusLabel(item.type) || item.type }}</td>
                <td>{{ item.technicianDisplayName || (item.technicianId ? '已指派' : '待指派') }}</td>
                <td>
                  <RouterLink :to="`/network-portal/work-orders/${item.workOrderId}`">
                    打开工作区
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
          <PageState
            v-else
            kind="empty"
            guide="今日（Asia/Shanghai）暂无未完成预约窗口。"
          />
        </section>
      </div>

      <p
        v-if="typeof data.pendingQualificationCount === 'number'"
        class="muted"
        data-testid="workbench-qualification-count"
      >
        待审资质 {{ data.pendingQualificationCount }} ·
        <RouterLink to="/network-portal/qualifications">查看资质</RouterLink>
      </p>
    </template>

    <p v-if="candidatesError" class="error">{{ candidatesError }}</p>
    <AssignTechnicianDrawer
      v-if="networkContextId"
      :open="drawerOpen"
      :network-context-id="networkContextId"
      :task="assignTask"
      :candidates="assignCandidates"
      :work-order-region-summary="workOrderRegionSummary"
      :ranking-explanation="rankingExplanation"
      :empty-reason="assignEmptyReason"
      :loading-candidates="loadingCandidates"
      @close="drawerOpen = false"
      @assigned="onAssigned"
    />
  </section>
</template>

<style scoped>
.workbench {
  display: grid;
  gap: 16px;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.subtitle,
.muted,
.gap-note {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.workbench-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}
.workbench-grid > .panel {
  min-width: 0;
  overflow: auto;
}
.workbench-grid > [data-testid='workbench-today-appointments'] {
  grid-column: 1 / -1;
}
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.timeline li {
  flex: 1 1 140px;
  min-width: 140px;
  max-width: 220px;
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-subtle);
  padding: 10px 12px;
  display: grid;
  gap: 4px;
}
.timeline li.active {
  border-color: var(--sos-primary-600);
  background: var(--sos-primary-100);
}
.timeline .count {
  font-size: 22px;
  font-weight: 650;
  color: var(--sos-color-text-primary);
}
.panel {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 14px 16px;
}
.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel__head h3 {
  margin: 0;
  font-size: 15px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  text-align: left;
  padding: 8px 6px;
  border-bottom: 1px solid var(--sos-color-border-light);
}
.capacity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}
.capacity-list li {
  display: grid;
  gap: 2px;
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: var(--sos-color-surface-subtle);
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button.primary {
  background: var(--sos-primary-600);
  border-color: var(--sos-primary-600);
  color: #fff;
}
.error {
  color: var(--sos-color-status-critical-fg);
}
@media (max-width: 1100px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
}
</style>
