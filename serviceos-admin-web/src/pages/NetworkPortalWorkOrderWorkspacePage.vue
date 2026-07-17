<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getNetworkPortalWorkOrderWorkspace,
  listNetworkPortalCorrections,
  listNetworkPortalExceptions,
  listNetworkPortalTaskAppointments,
  listNetworkPortalTaskContactAttempts,
  type NetworkPortalAppointment,
  type NetworkPortalContactAttempt,
  type NetworkPortalCorrectionItem,
  type NetworkPortalExceptionItem,
  type NetworkPortalWorkOrderWorkspace,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalWorkOrderWorkspace | null>(null)
const relatedCorrections = ref<NetworkPortalCorrectionItem[] | null>(null)
const relatedExceptions = ref<NetworkPortalExceptionItem[] | null>(null)
const relatedAppointments = ref<NetworkPortalAppointment[] | null>(null)
const relatedContactAttempts = ref<NetworkPortalContactAttempt[] | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function fanInByTaskId<T>(
  taskIds: string[],
  loader: (networkContextId: string, taskId: string) => Promise<T[]>,
): Promise<T[] | null> {
  if (!props.networkContextId || taskIds.length === 0) {
    return null
  }
  const results = await Promise.allSettled(
    taskIds.map((taskId) => loader(props.networkContextId!, taskId)),
  )
  if (results.some((result) => result.status === 'rejected')) {
    return null
  }
  return results.flatMap((result) => (result.status === 'fulfilled' ? result.value : []))
}

async function loadRelated(taskIds: string[]) {
  relatedCorrections.value = null
  relatedExceptions.value = null
  relatedAppointments.value = null
  relatedContactAttempts.value = null
  if (!props.networkContextId || taskIds.length === 0) {
    return
  }
  const taskSet = new Set(taskIds)
  try {
    const page = await listNetworkPortalCorrections(props.networkContextId, { status: 'OPEN' })
    relatedCorrections.value = page.items.filter(
      (item) => item.taskId != null && taskSet.has(item.taskId),
    )
  } catch {
    relatedCorrections.value = null
  }
  try {
    const page = await listNetworkPortalExceptions(props.networkContextId, { status: 'OPEN' })
    relatedExceptions.value = page.items.filter(
      (item) => item.taskId != null && taskSet.has(item.taskId),
    )
  } catch {
    relatedExceptions.value = null
  }
  // ADR-053：缺 manageAppointment 或任一次 403 时同时省略预约/联系区块，
  // 不得用空列表伪装无权限。
  const appointments = await fanInByTaskId(taskIds, listNetworkPortalTaskAppointments)
  const contacts = await fanInByTaskId(taskIds, listNetworkPortalTaskContactAttempts)
  if (appointments === null || contacts === null) {
    relatedAppointments.value = null
    relatedContactAttempts.value = null
  } else {
    relatedAppointments.value = appointments
    relatedContactAttempts.value = contacts
  }
}

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    relatedCorrections.value = null
    relatedExceptions.value = null
    relatedAppointments.value = null
    relatedContactAttempts.value = null
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
    await loadRelated(detail.value.taskIds)
  } catch (err) {
    detail.value = null
    relatedCorrections.value = null
    relatedExceptions.value = null
    relatedAppointments.value = null
    relatedContactAttempts.value = null
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
      只读薄快照（M213）+ 协作深链（M214）+ 预约/联系 fan-in（M215）：缺能力时省略相关区块。
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
        <div><dt>technicianId</dt><dd>{{ detail.technicianId ?? '—' }}</dd></div>
        <div><dt>effectiveFrom</dt><dd>{{ detail.effectiveFrom ?? '—' }}</dd></div>
        <div><dt>asOf</dt><dd data-testid="workspace-as-of">{{ detail.asOf }}</dd></div>
      </dl>

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
            <td>{{ task.technicianId ?? '—' }}</td>
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
        v-if="relatedAppointments"
        data-testid="workspace-related-appointments"
        class="related"
      >
        <h3>相关预约</h3>
        <ul v-if="relatedAppointments.length">
          <li
            v-for="item in relatedAppointments"
            :key="item.appointmentId"
            :data-testid="`workspace-related-appointment-${item.appointmentId}`"
          >
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="workspace-appointment-task-deeplink"
            >
              {{ item.appointmentId }}
            </RouterLink>
            （task {{ item.taskId }} · {{ item.type }} · {{ item.status }} · rev
            {{ item.currentRevisionNo }}）
          </li>
        </ul>
        <p v-else data-testid="workspace-related-appointments-empty">暂无相关预约</p>
      </section>

      <section
        v-if="relatedContactAttempts"
        data-testid="workspace-related-contacts"
        class="related"
      >
        <h3>相关联系尝试</h3>
        <ul v-if="relatedContactAttempts.length">
          <li
            v-for="item in relatedContactAttempts"
            :key="item.contactAttemptId"
            :data-testid="`workspace-related-contact-${item.contactAttemptId}`"
          >
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="workspace-contact-task-deeplink"
            >
              {{ item.contactAttemptId }}
            </RouterLink>
            （task {{ item.taskId }} · {{ item.channel }} · {{ item.resultCode }} ·
            {{ item.createdAt }}）
          </li>
        </ul>
        <p v-else data-testid="workspace-related-contacts-empty">暂无相关联系尝试</p>
      </section>

      <section
        v-if="relatedCorrections"
        data-testid="workspace-related-corrections"
        class="related"
      >
        <h3>相关 OPEN 整改</h3>
        <ul v-if="relatedCorrections.length">
          <li
            v-for="item in relatedCorrections"
            :key="item.correctionCaseId"
            :data-testid="`workspace-related-correction-${item.correctionCaseId}`"
          >
            <RouterLink :to="`/network-portal/corrections/${item.correctionCaseId}`">
              {{ item.correctionCaseId }}
            </RouterLink>
            （task {{ item.taskId }} · {{ item.status }}）
          </li>
        </ul>
        <p v-else data-testid="workspace-related-corrections-empty">暂无相关 OPEN 整改</p>
      </section>

      <section
        v-if="relatedExceptions"
        data-testid="workspace-related-exceptions"
        class="related"
      >
        <h3>相关 OPEN 异常</h3>
        <ul v-if="relatedExceptions.length">
          <li
            v-for="item in relatedExceptions"
            :key="item.exceptionId"
            :data-testid="`workspace-related-exception-${item.exceptionId}`"
          >
            <RouterLink :to="`/network-portal/exceptions/${item.exceptionId}`">
              {{ item.exceptionId }}
            </RouterLink>
            （task {{ item.taskId || '—' }} · {{ item.severity }} · {{ item.status }}）
          </li>
        </ul>
        <p v-else data-testid="workspace-related-exceptions-empty">暂无相关 OPEN 异常</p>
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
.hint {
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
</style>
