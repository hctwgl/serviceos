<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getNetworkPortalWorkOrderWorkspace,
  type NetworkPortalWorkOrderWorkspace,
  type NetworkPortalWorkspaceAppointmentSummary,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalWorkOrderWorkspace | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

function hasAppointmentWindow(item: NetworkPortalWorkspaceAppointmentSummary) {
  return item.windowStart != null && item.windowEnd != null
}

function resolveTechnician(technicianId: string | null | undefined) {
  if (!technicianId || !detail.value?.technicians) {
    return null
  }
  return (
    detail.value.technicians.find((tech) => tech.technicianProfileId === technicianId) ?? null
  )
}

const unassignedTaskIds = computed(() => {
  if (!detail.value) {
    return [] as string[]
  }
  return detail.value.tasks
    .filter((task) => !task.technicianId)
    .map((task) => task.taskId)
})

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!workOrderId.value) {
    detail.value = null
    error.value = '缺少 workOrderId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalWorkOrderWorkspace(
      props.networkContextId,
      workOrderId.value,
    )
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '工单工作区加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, workOrderId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-work-order-workspace"
    data-page-id="NETWORK.WORKORDER.WORKSPACE"
  >
    <header class="top">
      <div>
        <h2>限定工单工作区</h2>
        <p class="meta" data-testid="workspace-work-order-id">{{ workOrderId }}</p>
      </div>
      <div class="actions">
        <RouterLink to="/network-portal/work-orders" data-testid="workspace-back-to-list">
          返回工单列表
        </RouterLink>
        <button type="button" :disabled="loading" data-testid="workspace-refresh" @click="load">
          刷新
        </button>
      </div>
    </header>
    <p class="hint">
      只读薄快照（M213）+ 协作深链（M214）+ 服务端摘要 enrichment（M221～M228）：缺能力时省略相关区块。
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="workspace-loading">加载中…</p>
    <template v-else-if="detail">
      <dl data-testid="workspace-header-fields">
        <div><dt>networkId</dt><dd>{{ detail.networkId }}</dd></div>
        <div><dt>projectId</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
        <div>
          <dt>businessType</dt>
          <dd data-testid="workspace-business-type">{{ detail.businessType ?? '—' }}</dd>
        </div>
        <div>
          <dt>technicianId</dt>
          <dd data-testid="workspace-header-technician-id">
            <template v-if="resolveTechnician(detail.technicianId)">
              {{ resolveTechnician(detail.technicianId)!.displayName }}
              <span class="muted">（{{ detail.technicianId }}）</span>
            </template>
            <template v-else>{{ detail.technicianId ?? '—' }}</template>
          </dd>
        </div>
        <div><dt>effectiveFrom</dt><dd>{{ detail.effectiveFrom ?? '—' }}</dd></div>
        <div><dt>asOf</dt><dd data-testid="workspace-as-of">{{ detail.asOf }}</dd></div>
      </dl>

      <section
        v-if="detail.slaSummary"
        data-testid="workspace-sla-summary"
        class="related sla-summary"
        aria-label="SLA summary"
      >
        <h3>SLA 摘要</h3>
        <dl data-testid="workspace-sla-summary-fields">
          <div>
            <dt>openCount</dt>
            <dd data-testid="workspace-sla-open-count">{{ detail.slaSummary.openCount }}</dd>
          </div>
          <div>
            <dt>breachedCount</dt>
            <dd data-testid="workspace-sla-breached-count">{{ detail.slaSummary.breachedCount }}</dd>
          </div>
        </dl>
        <p class="hint">
          需 NETWORK <code>sla.read</code>。仅统计本网点 ACTIVE 任务上 RUNNING/BREACHED
          （breached ⊆ open）。无 SLA 详情表或深链。
        </p>
      </section>

      <section
        v-if="detail.visits"
        data-testid="workspace-visits"
        class="related"
        aria-label="Visit summaries"
      >
        <h3>Visit 摘要</h3>
        <ul v-if="detail.visits.length">
          <li
            v-for="visit in detail.visits"
            :key="visit.visitId"
            :data-testid="`workspace-visit-${visit.visitId}`"
          >
            <strong>{{ visit.visitId }}</strong>
            <span class="muted">
              （task {{ visit.taskId }} · seq {{ visit.visitSequence }} · {{ visit.status }} ·
              {{ visit.geofenceResult }} / {{ visit.policyDecision }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-visits-empty">暂无本网点 Visit</p>
        <p class="hint">
          需 NETWORK <code>visit.read</code>。不含 GPS/note/device。无独立 Visit 详情页。
        </p>
      </section>

      <section
        v-if="detail.formSubmissions"
        data-testid="workspace-form-submissions"
        class="related"
        aria-label="Form submission summaries"
      >
        <h3>表单提交摘要</h3>
        <ul v-if="detail.formSubmissions.length">
          <li
            v-for="row in detail.formSubmissions"
            :key="row.submissionId"
            :data-testid="`workspace-form-submission-${row.submissionId}`"
          >
            <strong>{{ row.formKey }}</strong>
            <span class="muted">
              （{{ row.submissionId }} · task {{ row.taskId }} · v{{ row.submissionVersion }} ·
              {{ row.validationStatus }} · err {{ row.errorCount }} / warn {{ row.warningCount }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-form-submissions-empty">暂无表单提交</p>
        <p class="hint">
          需 NETWORK <code>form.read</code>。不含 values/submittedBy。无表单 definition。
        </p>
      </section>

      <section
        v-if="detail.evidenceSlots"
        data-testid="workspace-evidence-slots"
        class="related"
        aria-label="Evidence slot summaries"
      >
        <h3>资料槽位摘要</h3>
        <ul v-if="detail.evidenceSlots.length">
          <li
            v-for="slot in detail.evidenceSlots"
            :key="slot.slotId"
            :data-testid="`workspace-evidence-slot-${slot.slotId}`"
          >
            <strong>{{ slot.requirementName }}</strong>
            <span class="muted">
              （{{ slot.requirementCode }} · {{ slot.mediaType }} · {{ slot.status }} ·
              task {{ slot.taskId }} · gen {{ slot.slotGeneration }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-evidence-slots-empty">暂无资料槽位</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。不含 definition/explanation JSON。无缩略图/下载。
        </p>
      </section>

      <section
        v-if="detail.evidenceItems"
        data-testid="workspace-evidence-items"
        class="related"
        aria-label="Evidence item summaries"
      >
        <h3>资料项摘要</h3>
        <ul v-if="detail.evidenceItems.length">
          <li
            v-for="item in detail.evidenceItems"
            :key="item.evidenceItemId"
            :data-testid="`workspace-evidence-item-${item.evidenceItemId}`"
          >
            <strong>{{ item.evidenceItemId }}</strong>
            <span class="muted">
              （slot {{ item.evidenceSlotId }} · #{{ item.itemOrdinal }} · {{ item.status }} ·
              rev {{ item.revisionCount }}
              <template v-if="item.latestRevisionStatus">
                / {{ item.latestRevisionStatus }}
              </template>
              ）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-evidence-items-empty">暂无资料项</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。不含 Revision 图/file/captureMetadata。
        </p>
      </section>

      <section
        v-if="detail.technicians"
        data-testid="workspace-current-technicians"
        class="related"
        aria-label="Current technician summaries"
      >
        <h3>当前师傅</h3>
        <ul v-if="detail.technicians.length">
          <li
            v-for="tech in detail.technicians"
            :key="tech.technicianProfileId"
            :data-testid="`workspace-technician-${tech.technicianProfileId}`"
          >
            <strong data-testid="workspace-technician-display-name">{{ tech.displayName }}</strong>
            <span class="muted">（{{ tech.technicianProfileId }} · {{ tech.membershipStatus }}）</span>
            <RouterLink
              :to="`/network-portal/technicians/memberships/${tech.membershipId}`"
              data-testid="workspace-technician-membership-deeplink"
            >
              关系详情
            </RouterLink>
            <RouterLink
              to="/network-portal/technicians"
              data-testid="workspace-technician-list-deeplink"
            >
              师傅列表
            </RouterLink>
          </li>
        </ul>
        <p v-else data-testid="workspace-current-technicians-empty">暂无已解析师傅</p>
        <ul v-if="unassignedTaskIds.length" data-testid="workspace-unassigned-tasks">
          <li v-for="taskId in unassignedTaskIds" :key="taskId">
            未指派任务
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId } }"
              data-testid="workspace-unassigned-task-deeplink"
            >
              {{ taskId }}
            </RouterLink>
          </li>
        </ul>
        <p class="hint">
          需 NETWORK <code>technician.readOwnNetwork</code>。字段对齐
          <code>NetworkPortalTechnicianItem</code>；仅含工作区 technicianId 命中项。
        </p>
      </section>

      <h3>本网点 ACTIVE 任务</h3>
      <table data-testid="workspace-tasks-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>阶段</th>
            <th>类型</th>
            <th>师傅</th>
            <th>协作深链</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="task in detail.tasks"
            :key="task.taskId"
            :data-testid="`workspace-task-${task.taskId}`"
          >
            <td>{{ task.taskId }}</td>
            <td>{{ task.status ?? '—' }}</td>
            <td>{{ task.stageCode ?? '—' }}</td>
            <td>{{ task.taskType ?? '—' }}</td>
            <td :data-testid="`workspace-task-technician-${task.taskId}`">
              <template v-if="resolveTechnician(task.technicianId)">
                {{ resolveTechnician(task.technicianId)!.displayName }}
              </template>
              <template v-else-if="task.technicianId">{{ task.technicianId }}</template>
              <template v-else>
                <RouterLink
                  :to="{ path: '/network-portal/tasks', query: { taskId: task.taskId } }"
                  data-testid="workspace-task-assign-deeplink"
                >
                  未指派
                </RouterLink>
              </template>
            </td>
            <td class="links">
              <RouterLink
                :to="{ path: '/network-portal/tasks', query: { taskId: task.taskId } }"
                data-testid="workspace-task-deeplink"
              >
                任务/预约
              </RouterLink>
              <RouterLink
                :to="{ path: '/network-portal/corrections', query: { taskId: task.taskId } }"
                data-testid="workspace-correction-deeplink"
              >
                整改
              </RouterLink>
              <RouterLink
                :to="{ path: '/network-portal/exceptions', query: { taskId: task.taskId } }"
                data-testid="workspace-exception-deeplink"
              >
                异常
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="detail.tasks.length === 0" data-testid="workspace-tasks-empty">暂无 ACTIVE 任务</p>

      <section
        v-if="detail.appointments"
        data-testid="workspace-related-appointments"
        class="related"
        aria-label="Appointment summaries"
      >
        <h3>预约摘要</h3>
        <ul v-if="detail.appointments.length">
          <li
            v-for="item in detail.appointments"
            :key="item.appointmentId"
            :data-testid="`workspace-related-appointment-${item.appointmentId}`"
          >
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="workspace-appointment-task-deeplink"
            >
              {{ item.appointmentId }}
            </RouterLink>
            <span class="muted">
              （task {{ item.taskId }} · {{ item.type }} · {{ item.status }} · rev
              {{ item.currentRevisionNo }}）
            </span>
            <span
              v-if="hasAppointmentWindow(item)"
              data-testid="workspace-appointment-window"
              class="window"
            >
              window {{ item.windowStart }} ~ {{ item.windowEnd }}
              （{{ item.timezone }} · {{ item.estimatedDurationMinutes }}min）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-appointments-empty">暂无预约摘要</p>
        <p class="hint">
          需 NETWORK <code>networkPortal.manageAppointment</code>。字段对齐 Admin
          工作区预约摘要；无 revisions/address。
        </p>
      </section>

      <section
        v-if="detail.contactAttempts"
        data-testid="workspace-related-contacts"
        class="related"
        aria-label="Contact attempt summaries"
      >
        <h3>联系尝试摘要</h3>
        <ul v-if="detail.contactAttempts.length">
          <li
            v-for="item in detail.contactAttempts"
            :key="item.contactAttemptId"
            :data-testid="`workspace-related-contact-${item.contactAttemptId}`"
          >
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="workspace-contact-task-deeplink"
            >
              {{ item.contactAttemptId }}
            </RouterLink>
            <span class="muted">
              （task {{ item.taskId }} · {{ item.channel }} · {{ item.resultCode }} ·
              {{ item.createdAt }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-contacts-empty">暂无联系尝试摘要</p>
        <p class="hint">
          需 NETWORK <code>networkPortal.manageAppointment</code>。字段对齐 Admin
          联系摘要；无 party/note/recording/actor。
        </p>
      </section>

      <section
        v-if="detail.corrections"
        data-testid="workspace-related-corrections"
        class="related"
        aria-label="Correction case summaries"
      >
        <h3>整改摘要</h3>
        <ul v-if="detail.corrections.length">
          <li
            v-for="item in detail.corrections"
            :key="item.correctionCaseId"
            :data-testid="`workspace-related-correction-${item.correctionCaseId}`"
          >
            <RouterLink :to="`/network-portal/corrections/${item.correctionCaseId}`">
              {{ item.correctionCaseId }}
            </RouterLink>
            <span class="muted">
              （task {{ item.taskId }} · {{ item.status }} ·
              reasons {{ item.reasonCodes.join(', ') || '—' }} ·
              resubmits {{ item.resubmissions.length }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-corrections-empty">暂无整改摘要</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。字段对齐 Admin 工作区整改摘要；无 createdBy/waiveNote。
        </p>
      </section>

      <section
        v-if="detail.exceptions"
        data-testid="workspace-related-exceptions"
        class="related"
        aria-label="Operational exception summaries"
      >
        <h3>异常摘要</h3>
        <ul v-if="detail.exceptions.length">
          <li
            v-for="item in detail.exceptions"
            :key="item.exceptionId"
            :data-testid="`workspace-related-exception-${item.exceptionId}`"
          >
            <RouterLink :to="`/network-portal/exceptions/${item.exceptionId}`">
              {{ item.exceptionId }}
            </RouterLink>
            <span class="muted">
              （task {{ item.taskId || '—' }} · {{ item.severity }} · {{ item.status }} ·
              {{ item.errorCode }}）
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-exceptions-empty">暂无异常摘要</p>
        <p class="hint">
          需 NETWORK <code>operations.exception.read</code>。字段对齐队列
          <code>NetworkPortalExceptionItem</code>；allowedActions 恒为空；含全部状态。
        </p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.meta,
.hint,
.muted {
  color: #5b6573;
  font-size: 0.9rem;
}
.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
dl {
  display: grid;
  gap: 0.35rem;
  margin: 1rem 0;
}
dt {
  font-size: 0.75rem;
  color: #5b6573;
}
dd {
  margin: 0 0 0.35rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.45rem 0.35rem;
  text-align: left;
  font-size: 0.85rem;
}
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.related {
  margin-top: 1.25rem;
}
.related ul {
  padding-left: 1.1rem;
}
.sla-summary {
  padding: 0.75rem 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}
.window {
  display: block;
  margin-top: 0.2rem;
  color: #374151;
  font-size: 0.8rem;
}
</style>
