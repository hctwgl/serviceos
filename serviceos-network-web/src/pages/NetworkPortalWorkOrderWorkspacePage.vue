<script setup lang="ts">
import { statusLabel } from '../product/labels'
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
          <dd data-testid="workspace-business-type">{{ detail.businessType ? statusLabel(detail.businessType) : '—' }}</dd>
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
              （task {{ visit.taskId }} · seq {{ visit.visitSequence }} · {{ visit.status ? statusLabel(visit.status) : '—' }} ·
              {{ visit.geofenceResult }} / {{ visit.policyDecision }} · v{{ visit.aggregateVersion }}）
            </span>
            <span class="muted" data-testid="workspace-visit-appointment">
              · appointment {{ visit.appointmentId }}
            </span>
            <span class="muted" data-testid="workspace-visit-technician">
              · technician {{ visit.technicianId }}
            </span>
            <span class="muted" data-testid="workspace-visit-network">
              · network {{ visit.networkId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-visit-checkin">
              · check-in {{ visit.checkInCapturedAt }} / recv {{ visit.checkInReceivedAt }}
            </span>
            <span class="muted" data-testid="workspace-visit-checkout">
              · check-out {{ visit.checkOutCapturedAt ?? '—' }} / recv
              {{ visit.checkOutReceivedAt ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-visit-result">
              · result {{ visit.resultCode ?? '—' }} / exception {{ visit.exceptionCode ?? '—' }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-visits-empty">暂无本网点 Visit</p>
        <p class="hint">
          需 NETWORK <code>visit.read</code>。展示 Accepted 非 PII 摘要字段；不含
          GPS/note/device。无独立 Visit 详情页。
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
            <span class="muted" data-testid="workspace-form-project">
              · project {{ row.projectId }}
            </span>
            <span class="muted" data-testid="workspace-form-version">
              · formVersion {{ row.formVersionId }}
            </span>
            <span class="muted" data-testid="workspace-form-submitted-at">
              · submittedAt {{ row.submittedAt }}
            </span>
            <span class="muted" data-testid="workspace-form-digest">
              · digest {{ row.contentDigest }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-form-submissions-empty">暂无表单提交</p>
        <p class="hint">
          需 NETWORK <code>form.read</code>。展示 Accepted 非 PII 摘要字段；不含
          values/submittedBy。无表单 definition。
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
              （{{ slot.requirementCode ? statusLabel(slot.requirementCode) : '—' }} · {{ slot.mediaType ? statusLabel(slot.mediaType) : '—' }} · {{ slot.status ? statusLabel(slot.status) : '—' }} ·
              task {{ slot.taskId }} · gen {{ slot.slotGeneration }}）
            </span>
            <span class="muted" data-testid="workspace-evidence-slot-template">
              · template {{ slot.templateKey }}@{{ slot.templateVersion }}
            </span>
            <span class="muted" data-testid="workspace-evidence-slot-counts">
              · required {{ slot.required }} · min {{ slot.minCount }} / max
              {{ slot.maxCount ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-evidence-slot-state">
              · active {{ slot.active }} · {{ slot.transition }} · disposition
              {{ slot.requiredDisposition }}
            </span>
            <span class="muted" data-testid="workspace-evidence-slot-resolved">
              · resolvedAt {{ slot.resolvedAt }} · occurrence {{ slot.occurrenceKey }} · project
              {{ slot.projectId }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-evidence-slots-empty">暂无资料槽位</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。展示 Accepted 非 PII 槽位摘要；不含
          definition/explanation JSON。无缩略图/下载。
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
              （slot {{ item.evidenceSlotId }} · #{{ item.itemOrdinal }} · {{ item.status ? statusLabel(item.status) : '—' }} ·
              rev {{ item.revisionCount }}
              <template v-if="item.latestRevisionNumber != null">
                / #{{ item.latestRevisionNumber }}
              </template>
              <template v-if="item.latestRevisionStatus">
                / {{ statusLabel(item.latestRevisionStatus) }}
              </template>
              ）
            </span>
            <span class="muted" data-testid="workspace-evidence-item-project">
              · project {{ item.projectId }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-evidence-items-empty">暂无资料项</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。展示 Accepted 非 PII 资料项摘要；不含
          Revision 图/file/captureMetadata。
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
            <span class="muted" data-testid="workspace-technician-principal">
              · principal {{ tech.principalId }} · profile {{ tech.profileStatus }}
            </span>
            <span class="muted" data-testid="workspace-technician-validity">
              · valid {{ tech.validFrom }} → {{ tech.validTo ?? '—' }}
              <template v-if="tech.membershipVersion != null">
                · v{{ tech.membershipVersion }}
              </template>
            </span>
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
            <td>{{ task.status ? statusLabel(task.status) : '—' }}</td>
            <td>{{ task.stageCode ? statusLabel(task.stageCode) : '—' }}</td>
            <td>{{ task.taskType ? statusLabel(task.taskType) : '—' }}</td>
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
              （task {{ item.taskId }} · {{ item.type ? statusLabel(item.type) : '—' }} · {{ item.status ? statusLabel(item.status) : '—' }} · rev
              {{ item.currentRevisionNo }} · v{{ item.aggregateVersion }}）
            </span>
            <span
              v-if="hasAppointmentWindow(item)"
              data-testid="workspace-appointment-window"
              class="window"
            >
              window {{ item.windowStart }} ~ {{ item.windowEnd }}
              （{{ item.timezone }} · {{ item.estimatedDurationMinutes }}min）
            </span>
            <span class="muted" data-testid="workspace-appointment-network">
              · network {{ item.assignedNetworkId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-appointment-technician">
              · technician {{ item.technicianId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-appointment-created-at">
              · createdAt {{ item.createdAt }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-appointments-empty">暂无预约摘要</p>
        <p class="hint">
          需 NETWORK <code>networkPortal.manageAppointment</code>。展示 Accepted 非 PII
          预约摘要字段；无 revisions/address/actor。
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
            <span class="muted" data-testid="workspace-contact-scope">
              · project {{ item.projectId }} · workOrder {{ item.workOrderId }}
            </span>
            <span class="muted" data-testid="workspace-contact-window">
              · {{ item.startedAt }} → {{ item.endedAt }}
            </span>
            <span class="muted" data-testid="workspace-contact-next">
              · nextContactAt {{ item.nextContactAt ?? '—' }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-contacts-empty">暂无联系尝试摘要</p>
        <p class="hint">
          需 NETWORK <code>networkPortal.manageAppointment</code>。展示 Accepted 非 PII
          联系摘要字段；无 party/note/recording/actor。
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
              （task {{ item.taskId }} · {{ item.status ? statusLabel(item.status) : '—' }} ·
              reasons {{ item.reasonCodes.join(', ') || '—' }} ·
              sourceReview {{ item.sourceReviewCaseId || '—' }} ·
              resubmits {{ item.resubmissions.length }}）
            </span>
            <span class="muted" data-testid="workspace-correction-project">
              · project {{ item.projectId }}
            </span>
            <span class="muted" data-testid="workspace-correction-decision">
              · sourceDecision {{ item.sourceReviewDecisionId || '—' }}
            </span>
            <span class="muted" data-testid="workspace-correction-task">
              · correctionTask
              <RouterLink
                v-if="item.correctionTaskId"
                :to="{ path: '/network-portal/tasks', query: { taskId: item.correctionTaskId } }"
                data-testid="workspace-correction-task-deeplink"
              >
                {{ item.correctionTaskId }}
              </RouterLink>
              <template v-else>—</template>
            </span>
            <span class="muted" data-testid="workspace-correction-times">
              · createdAt {{ item.createdAt }} · closedAt {{ item.closedAt ?? '—' }} · waivedAt
              {{ item.waivedAt ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-correction-snapshot">
              · latestSnapshot {{ item.latestResubmissionSnapshotId ?? '—' }}
            </span>
            <span
              v-if="item.resubmissions.length"
              class="muted"
              data-testid="workspace-correction-latest-resubmission"
            >
              · latestResubmit #{{ item.resubmissions[item.resubmissions.length - 1].resubmissionOrdinal }}
              @ {{ item.resubmissions[item.resubmissions.length - 1].submittedAt }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-corrections-empty">暂无整改摘要</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。展示 Accepted 非 PII 整改摘要字段；无
          createdBy/waiveNote。
        </p>
      </section>

      <section
        v-if="detail.reviews"
        data-testid="workspace-related-reviews"
        class="related"
        aria-label="Review case summaries"
      >
        <h3>审核摘要</h3>
        <ul v-if="detail.reviews.length">
          <li
            v-for="item in detail.reviews"
            :key="item.reviewCaseId"
            :data-testid="`workspace-related-review-${item.reviewCaseId}`"
          >
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="workspace-review-task-deeplink"
            >
              {{ item.reviewCaseId }}
            </RouterLink>
            <span class="muted">
              （task {{ item.taskId }} · {{ item.origin ? statusLabel(item.origin) : '—' }} · {{ item.status ? statusLabel(item.status) : '—' }} ·
              decisions {{ item.decisions.length }}）
            </span>
            <span class="muted" data-testid="workspace-review-project">
              · project {{ item.projectId }} · scope {{ item.scopeType }} · policy
              {{ item.policyVersion }}
            </span>
            <span class="muted" data-testid="workspace-review-snapshot">
              · snapshot {{ item.evidenceSetSnapshotId }}
            </span>
            <span class="muted" data-testid="workspace-review-times">
              · createdAt {{ item.createdAt }} · decidedAt {{ item.decidedAt ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-review-refs">
              · sourceReview {{ item.sourceReviewCaseId ?? '—' }} · external
              {{ item.externalSubmissionRef ?? '—' }} · callback
              {{ item.callbackBatchRef ?? '—' }} · mapping {{ item.mappingVersionId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-review-reopen">
              · reopenedFrom {{ item.reopenedFromReviewCaseId ?? '—' }} · trigger
              {{ item.reopenTriggerRef ?? '—' }}
            </span>
            <span
              v-if="item.decisions.length"
              class="muted"
              data-testid="workspace-review-latest-decision"
            >
              · latestDecision
              {{ item.decisions[item.decisions.length - 1].decision }} /
              {{ item.decisions[item.decisions.length - 1].decisionSource }} @
              {{ item.decisions[item.decisions.length - 1].decidedAt }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-reviews-empty">暂无审核摘要</p>
        <p class="hint">
          需 NETWORK <code>evidence.read</code>。总部审核中：网点可代补/整改协作，不可独立裁决（无 DecideReview）。
          展示 Accepted 非 PII 审核摘要；无 note/approvalRef/decidedBy/createdBy。
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
              （task {{ item.taskId || '—' }} · {{ item.severity ? statusLabel(item.severity) : '—' }} · {{ item.status ? statusLabel(item.status) : '—' }} ·
              {{ item.errorCode }}）
            </span>
            <span class="muted" data-testid="workspace-exception-taxonomy">
              · {{ item.sourceType }} / {{ item.category }} · project
              {{ item.projectId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-exception-work-order">
              · workOrder {{ item.workOrderId ?? '—' }}
            </span>
            <span class="muted" data-testid="workspace-exception-handling">
              · handlingTask
              <RouterLink
                v-if="item.handlingTaskId"
                :to="{ path: '/network-portal/tasks', query: { taskId: item.handlingTaskId } }"
                data-testid="workspace-exception-handling-deeplink"
              >
                {{ item.handlingTaskId }}
              </RouterLink>
              <template v-else>—</template>
            </span>
            <span class="muted" data-testid="workspace-exception-counts">
              · occurrences {{ item.occurrenceCount }}
            </span>
            <span class="muted" data-testid="workspace-exception-times">
              · openedAt {{ item.openedAt }} · lastDetectedAt {{ item.lastDetectedAt }} ·
              resolvedAt {{ item.resolvedAt ?? '—' }} · resolution
              {{ item.resolutionCode ?? '—' }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workspace-related-exceptions-empty">暂无异常摘要</p>
        <p class="hint">
          需 NETWORK <code>operations.exception.read</code>。展示 Accepted 非 PII
          异常摘要字段；allowedActions 恒为空；含全部状态。
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
