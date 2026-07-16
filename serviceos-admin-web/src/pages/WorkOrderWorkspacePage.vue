<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getWorkOrderActivitySummary,
  getWorkOrderWorkspace,
  getWorkOrderWorkspaceSection,
  type SectionCode,
  type WorkOrderActivitySummary,
  type WorkOrderWorkspace,
  type WorkOrderWorkspaceSection,
} from '../api/workspace'
import { listWorkOrderSlaInstances, type SlaInstancePage } from '../api/sla'
import {
  getAuthorizedWorkOrder,
  getAuthorizedWorkOrderStages,
  listAuthorizedWorkOrderTasks,
  listWorkOrderCoreTimeline,
  type WorkOrderDetail,
  type WorkOrderTaskPage,
  type WorkOrderTimelinePage,
  type WorkflowExecutionProjection,
} from '../api/workOrderDetail'
import { getTaskAllowedActions, type TaskAllowedActions } from '../api/tasks'
import TaskCommandPanel from '../components/TaskCommandPanel.vue'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const workspace = ref<WorkOrderWorkspace | null>(null)
const activity = ref<WorkOrderActivitySummary | null>(null)
const allowedActions = ref<TaskAllowedActions | null>(null)
const allowedActionsError = ref<string | null>(null)
const activeSection = ref<SectionCode>('TASKS')
const sectionLoading = ref(false)
const sectionError = ref<string | null>(null)
const sectionData = ref<WorkOrderWorkspaceSection | null>(null)
const slaPage = ref<SlaInstancePage | null>(null)
const slaError = ref<string | null>(null)
const workOrderDetail = ref<WorkOrderDetail | null>(null)
const stages = ref<WorkflowExecutionProjection | null>(null)
const taskPage = ref<WorkOrderTaskPage | null>(null)
const timelinePage = ref<WorkOrderTimelinePage | null>(null)
const authorityError = ref<string | null>(null)

const sections: SectionCode[] = [
  'TASKS',
  'TIMELINE_AUDIT',
  'APPOINTMENTS_VISITS',
  'FORMS_EVIDENCE',
  'REVIEWS_CORRECTIONS',
  'INTEGRATION',
]

async function loadAllowedActions(taskId: string | undefined) {
  allowedActions.value = null
  allowedActionsError.value = null
  if (!taskId) {
    return
  }
  try {
    const result = await getTaskAllowedActions(taskId)
    allowedActions.value = result.data
  } catch (err) {
    allowedActionsError.value = err instanceof Error ? err.message : '加载 allowed-actions 失败'
  }
}

async function loadSlaInstances() {
  slaError.value = null
  try {
    slaPage.value = await listWorkOrderSlaInstances(workOrderId.value, { limit: '20' })
  } catch (err) {
    slaError.value = err instanceof Error ? err.message : '加载工单 SLA 失败'
    slaPage.value = null
  }
}

async function loadAuthorityProjections() {
  authorityError.value = null
  try {
    const [detail, stageProjection, tasks, timeline] = await Promise.all([
      getAuthorizedWorkOrder(workOrderId.value),
      getAuthorizedWorkOrderStages(workOrderId.value),
      listAuthorizedWorkOrderTasks(workOrderId.value, { limit: '20' }),
      listWorkOrderCoreTimeline(workOrderId.value, { limit: '20' }),
    ])
    workOrderDetail.value = detail
    stages.value = stageProjection
    taskPage.value = tasks
    timelinePage.value = timeline
  } catch (err) {
    authorityError.value = err instanceof Error ? err.message : '加载工单权威投影失败'
    workOrderDetail.value = null
    stages.value = null
    taskPage.value = null
    timelinePage.value = null
  }
}

async function loadWorkspace() {
  loading.value = true
  error.value = null
  try {
    const [ws, act] = await Promise.all([
      getWorkOrderWorkspace(workOrderId.value),
      getWorkOrderActivitySummary(workOrderId.value),
    ])
    workspace.value = ws
    activity.value = act
    await Promise.all([
      loadAllowedActions(ws.currentTaskSummary?.taskId),
      loadSlaInstances(),
      loadAuthorityProjections(),
    ])
    const firstAvailable = sections.find(
      (code) => ws.sectionAvailability[code] === 'AVAILABLE' || ws.sectionAvailability[code] === 'EMPTY',
    )
    activeSection.value = firstAvailable ?? 'TASKS'
    await loadSection(activeSection.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载工作区失败'
    workspace.value = null
    activity.value = null
    allowedActions.value = null
    slaPage.value = null
    workOrderDetail.value = null
    stages.value = null
    taskPage.value = null
    timelinePage.value = null
  } finally {
    loading.value = false
  }
}

async function loadSection(section: SectionCode) {
  activeSection.value = section
  sectionLoading.value = true
  sectionError.value = null
  try {
    sectionData.value = await getWorkOrderWorkspaceSection(workOrderId.value, section, { limit: '20' })
  } catch (err) {
    sectionError.value = err instanceof Error ? err.message : '加载区块失败'
    sectionData.value = null
  } finally {
    sectionLoading.value = false
  }
}

const sectionPreview = computed(() => {
  const data = sectionData.value
  if (!data) return '—'
  const payload =
    data.tasks ??
    data.timeline ??
    data.appointmentsVisits ??
    data.formsEvidence ??
    data.reviewsCorrections ??
    data.integration
  return JSON.stringify(payload, null, 2)
})

const slaRows = computed(() =>
  (slaPage.value?.items ?? []).map((item) => ({
    slaInstanceId: item.slaInstanceId,
    slaRef: item.slaRef,
    status: item.status,
    deadlineAt: item.deadlineAt,
    remainingSeconds: item.remainingSeconds,
    overdueSeconds: item.overdueSeconds,
    taskId: item.taskId,
  })),
)
const stageRows = computed(() =>
  (stages.value?.stages ?? []).map((item) => ({
    id: item.id,
    stageCode: item.stageCode,
    sequenceNo: item.sequenceNo,
    status: item.status,
    activatedAt: item.activatedAt,
    completedAt: item.completedAt,
  })),
)
const taskRows = computed(() =>
  (taskPage.value?.items ?? []).map((item) => ({
    id: item.id,
    taskType: item.taskType,
    taskKind: item.taskKind,
    status: item.status,
    stageCode: item.stageCode,
    priority: item.priority,
    version: item.version,
  })),
)
const timelineRows = computed(() =>
  (timelinePage.value?.items ?? []).map((item) => ({
    id: item.id,
    category: item.category,
    eventType: item.eventType,
    occurredAt: item.occurredAt,
    resourceType: item.resourceType,
    resourceId: item.resourceId,
    outcomeCode: item.outcomeCode,
  })),
)

watch(workOrderId, () => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})

onMounted(() => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})
</script>

<template>
  <section class="workspace">
    <header class="top">
      <div>
        <h2>工单工作区</h2>
        <p class="meta">{{ workOrderId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="loadWorkspace">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="workspace">
      <div class="grid">
        <article class="card">
          <h3>概览</h3>
          <dl>
            <div><dt>状态</dt><dd>{{ workspace.header.status }}</dd></div>
            <div><dt>项目</dt><dd>{{ workspace.header.projectId }}</dd></div>
            <div><dt>外部单号</dt><dd>{{ workspace.header.externalOrderCode || '—' }}</dd></div>
            <div><dt>时间线 freshness</dt><dd>{{ workspace.timelineFreshnessStatus }}</dd></div>
            <div><dt>asOf</dt><dd>{{ workspace.meta.asOf }}</dd></div>
            <div><dt>allowed-actions</dt><dd>{{ workspace.allowedActionLink || '—' }}</dd></div>
          </dl>
        </article>

        <article class="card">
          <h3>当前任务 / 责任 / SLA / 异常</h3>
          <dl>
            <div>
              <dt>当前任务</dt>
              <dd v-if="workspace.currentTaskSummary">
                {{ workspace.currentTaskSummary.taskType }} /
                {{ workspace.currentTaskSummary.status }}
                <small>{{ workspace.currentTaskSummary.taskId }}</small>
              </dd>
              <dd v-else>—</dd>
            </div>
            <div>
              <dt>服务责任</dt>
              <dd v-if="workspace.serviceAssignmentSummary">
                network {{ String(workspace.serviceAssignmentSummary.networkId ?? '—') }} /
                tech {{ String(workspace.serviceAssignmentSummary.technicianId ?? '—') }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div>
              <dt>SLA</dt>
              <dd v-if="workspace.slaSummary">
                open {{ Number(workspace.slaSummary.openCount ?? 0) }} /
                breached {{ Number(workspace.slaSummary.breachedCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div>
              <dt>异常</dt>
              <dd v-if="workspace.exceptionSummary">
                open {{ Number(workspace.exceptionSummary.openCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
          </dl>
        </article>

        <article class="card">
          <h3>最近活动</h3>
          <ul v-if="activity?.items?.length">
            <li v-for="(item, index) in activity.items" :key="index">
              <strong>{{ item.eventType || item.type || 'event' }}</strong>
              <span>{{ item.occurredAt || '—' }}</span>
              <small>{{ item.resourceType }} {{ item.resourceId }}</small>
            </li>
          </ul>
          <p v-else>暂无活动摘要</p>
        </article>

        <article class="card">
          <h3>当前任务命令</h3>
          <p class="meta">按钮仅来自服务端 allowed-actions；执行后重新拉取工作区。</p>
          <p v-if="allowedActionsError" class="error">{{ allowedActionsError }}</p>
          <p v-else-if="!workspace.currentTaskSummary">无当前任务</p>
          <TaskCommandPanel
            v-else-if="allowedActions"
            :task-id="workspace.currentTaskSummary.taskId"
            :allowed-actions="allowedActions"
            @executed="loadWorkspace"
          />
          <p v-else>暂无允许动作或无权读取</p>
          <ul v-if="allowedActions?.actions?.length" class="action-list">
            <li v-for="action in allowedActions.actions" :key="action.code">
              <strong>{{ action.label }}</strong>
              <span>{{ action.code }}</span>
              <small>
                obligations:
                {{ action.obligations.length ? action.obligations.join(', ') : 'none' }}
              </small>
            </li>
          </ul>
          <p v-if="allowedActions" class="meta">
            asOf {{ allowedActions.asOf }} / v{{ allowedActions.resourceVersion }}
          </p>
        </article>
      </div>

      <article v-if="workOrderDetail" class="card">
        <h3>工单权威事实</h3>
        <dl>
          <div><dt>status</dt><dd>{{ workOrderDetail.workOrder.status }}</dd></div>
          <div><dt>clientCode</dt><dd>{{ workOrderDetail.workOrder.clientCode }}</dd></div>
          <div><dt>brandCode</dt><dd>{{ workOrderDetail.workOrder.brandCode }}</dd></div>
          <div><dt>serviceProductCode</dt><dd>{{ workOrderDetail.workOrder.serviceProductCode }}</dd></div>
          <div><dt>externalOrderCode</dt><dd>{{ workOrderDetail.workOrder.externalOrderCode }}</dd></div>
          <div><dt>version</dt><dd>{{ workOrderDetail.workOrder.version }}</dd></div>
          <div><dt>asOf</dt><dd>{{ workOrderDetail.asOf }}</dd></div>
        </dl>
      </article>
      <p v-if="authorityError" class="error">{{ authorityError }}</p>

      <article v-if="stages" class="card">
        <h3>Workflow / Stage</h3>
        <p class="meta">
          workflow={{ stages.workflow?.workflowKey || '—' }} /
          {{ stages.workflow?.status || '未初始化' }} /
          asOf {{ stages.asOf }}
        </p>
      </article>
      <QueueTable
        title="Stage 投影"
        :columns="['id', 'stageCode', 'sequenceNo', 'status', 'activatedAt', 'completedAt']"
        :rows="stageRows"
        :loading="false"
        :error="null"
        :as-of="stages?.asOf"
        :next-cursor="null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />

      <QueueTable
        title="工单 Task 摘要"
        :columns="['id', 'taskType', 'taskKind', 'status', 'stageCode', 'priority', 'version']"
        :rows="taskRows"
        :loading="false"
        :error="null"
        :as-of="taskPage?.asOf"
        :next-cursor="taskPage?.nextCursor ?? null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />
      <p v-if="taskPage?.items?.length" class="links">
        打开任务：
        <RouterLink
          v-for="item in taskPage.items"
          :key="item.id"
          :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.id } }"
        >
          {{ item.taskType }} / {{ item.id }}
        </RouterLink>
      </p>

      <QueueTable
        title="核心时间线"
        :columns="['id', 'category', 'eventType', 'occurredAt', 'resourceType', 'resourceId', 'outcomeCode']"
        :rows="timelineRows"
        :loading="false"
        :error="null"
        :as-of="timelinePage?.asOf"
        :next-cursor="timelinePage?.nextCursor ?? null"
        @refresh="loadAuthorityProjections"
        @next="() => undefined"
      />
      <p v-if="timelinePage" class="meta">
        freshness={{ timelinePage.freshnessStatus }} / resourceVersion={{ timelinePage.resourceVersion }} /
        lastProjectedAt={{ timelinePage.lastProjectedAt || '—' }}
      </p>

      <QueueTable
        title="工单 SLA 实例"
        :columns="['slaInstanceId', 'slaRef', 'status', 'deadlineAt', 'remainingSeconds', 'overdueSeconds', 'taskId']"
        :rows="slaRows"
        :loading="false"
        :error="slaError"
        :as-of="slaPage?.asOf"
        :next-cursor="slaPage?.nextCursor ?? null"
        @refresh="loadSlaInstances"
        @next="() => undefined"
      />
      <p v-if="slaPage?.items?.length" class="links">
        打开 SLA 详情：
        <RouterLink
          v-for="item in slaPage.items"
          :key="item.slaInstanceId"
          :to="{ name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } }"
        >
          {{ item.slaRef || item.slaInstanceId }}
        </RouterLink>
      </p>

      <article class="card sections">
        <h3>按需区块</h3>
        <div class="tabs">
          <button
            v-for="code in sections"
            :key="code"
            type="button"
            :class="{ active: activeSection === code }"
            :disabled="workspace.sectionAvailability[code] === 'UNAVAILABLE'"
            @click="loadSection(code)"
          >
            {{ code }}
            <em>{{ workspace.sectionAvailability[code] || '?' }}</em>
          </button>
        </div>
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <pre v-else>{{ sectionPreview }}</pre>
      </article>
    </template>
  </section>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
h2,
h3 {
  margin: 0 0 0.75rem;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.55rem;
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
}
dd small {
  display: block;
  color: #829ab1;
  font-family: ui-monospace, monospace;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}
li {
  display: grid;
  gap: 0.15rem;
}
li span,
li small {
  color: #627d98;
  font-size: 0.85rem;
}
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
}
.tabs button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 999px;
  padding: 0.35rem 0.7rem;
  cursor: pointer;
}
.tabs button.active {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
}
.tabs button em {
  font-style: normal;
  margin-left: 0.35rem;
  opacity: 0.75;
  font-size: 0.75rem;
}
.tabs button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
pre {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.8rem;
}
.error {
  color: #9b1c1c;
}
.action-list {
  list-style: none;
  margin: 0.75rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.35rem;
}
.action-list small {
  display: block;
  color: #829ab1;
}
.links {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
