<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  assignNetworkPortalTechnician,
  cancelNetworkPortalAppointment,
  confirmNetworkPortalAppointment,
  listNetworkPortalTaskAppointments,
  listNetworkPortalTasks,
  listNetworkPortalTechnicians,
  proposeNetworkPortalAppointment,
  rescheduleNetworkPortalAppointment,
  type NetworkPortalAppointment,
  type NetworkPortalTaskItem,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'
import { getMe } from '../api/me'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTaskItem[]>([])
const technicians = ref<NetworkPortalTechnicianItem[]>([])
const appointments = ref<NetworkPortalAppointment[]>([])
const error = ref<string | null>(null)
const selectedTaskId = ref('')
const selectedTechnicianId = ref('')
const businessType = ref('INSTALLATION')
const assignBusy = ref(false)
const assignError = ref<string | null>(null)
const assignMessage = ref<string | null>(null)

const appointmentType = ref('SURVEY')
const addressRef = ref('network-portal-address')
const addressVersion = ref('v1')
const windowStart = ref('2026-08-20T01:00:00.000Z')
const windowEnd = ref('2026-08-20T04:00:00.000Z')
const appointmentBusy = ref(false)
const appointmentError = ref<string | null>(null)
const appointmentMessage = ref<string | null>(null)
const rescheduleReason = ref('CUSTOMER_REQUESTED_LATER')
const cancelReason = ref('CUSTOMER_CANCELLED')
const rescheduleStart = ref('2026-09-11T02:00:00.000Z')
const rescheduleEnd = ref('2026-09-11T05:00:00.000Z')

async function load() {
  if (!props.networkContextId) {
    items.value = []
    technicians.value = []
    appointments.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const taskPage = await listNetworkPortalTasks(props.networkContextId)
    items.value = taskPage.items
    error.value = null
    if (!selectedTaskId.value && items.value.length > 0) {
      selectedTaskId.value = items.value[0].taskId
      if (items.value[0].businessType) {
        businessType.value = items.value[0].businessType
      }
    }
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '任务列表加载失败'
  }
  try {
    const techPage = await listNetworkPortalTechnicians(props.networkContextId)
    technicians.value = techPage.items
    if (!selectedTechnicianId.value && technicians.value.length > 0) {
      selectedTechnicianId.value = technicians.value[0].technicianProfileId
    }
  } catch {
    technicians.value = []
  }
  await loadAppointments()
}

async function loadAppointments() {
  appointments.value = []
  if (!props.networkContextId || !selectedTaskId.value) {
    return
  }
  try {
    const page = await listNetworkPortalTaskAppointments(
      props.networkContextId,
      selectedTaskId.value,
    )
    appointments.value = page
  } catch {
    appointments.value = []
  }
}

async function submitAssign() {
  if (!props.networkContextId) {
    assignError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value || !selectedTechnicianId.value) {
    assignError.value = '请选择任务与师傅'
    return
  }
  assignBusy.value = true
  assignError.value = null
  assignMessage.value = null
  try {
    const result = await assignNetworkPortalTechnician(props.networkContextId, selectedTaskId.value, {
      technicianAssigneeId: selectedTechnicianId.value,
      businessType: businessType.value.trim() || 'INSTALLATION',
    })
    assignMessage.value =
      `已指派 network=${result.data.networkAssigneeId} tech=${result.data.technicianAssigneeId}`
    await load()
  } catch (err) {
    assignError.value = err instanceof Error ? err.message : '指派师傅失败'
  } finally {
    assignBusy.value = false
  }
}

async function submitPropose() {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  if (!selectedTaskId.value) {
    appointmentError.value = '请选择任务'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await proposeNetworkPortalAppointment(
      props.networkContextId,
      selectedTaskId.value,
      {
        type: appointmentType.value,
        window: {
          start: windowStart.value,
          end: windowEnd.value,
          timezone: 'Asia/Shanghai',
          estimatedDurationMinutes: 120,
        },
        addressRef: addressRef.value.trim() || 'network-portal-address',
        addressVersion: addressVersion.value.trim() || 'v1',
      },
    )
    appointmentMessage.value =
      `已提议 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '提议预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitConfirm(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const me = await getMe()
    const result = await confirmNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        confirmedPartyType: 'NETWORK_MEMBER',
        confirmedPartyRef: me.principalId,
        confirmationChannel: 'PHONE',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已确认 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '确认预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitReschedule(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await rescheduleNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        newWindow: {
          start: rescheduleStart.value,
          end: rescheduleEnd.value,
          timezone: 'Asia/Shanghai',
          estimatedDurationMinutes: 120,
        },
        reasonCode: rescheduleReason.value.trim() || 'CUSTOMER_REQUESTED_LATER',
        note: 'Network Portal 改约',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已改约 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '改约失败'
  } finally {
    appointmentBusy.value = false
  }
}

async function submitCancel(item: NetworkPortalAppointment) {
  if (!props.networkContextId) {
    appointmentError.value = '请选择 NETWORK 上下文'
    return
  }
  appointmentBusy.value = true
  appointmentError.value = null
  appointmentMessage.value = null
  try {
    const result = await cancelNetworkPortalAppointment(
      props.networkContextId,
      item.appointmentId,
      {
        reasonCode: cancelReason.value.trim() || 'CUSTOMER_CANCELLED',
        note: 'Network Portal 取消',
      },
      item.aggregateVersion,
    )
    appointmentMessage.value =
      `已取消 appointment=${result.data.appointmentId} status=${result.data.status}`
    await loadAppointments()
  } catch (err) {
    appointmentError.value = err instanceof Error ? err.message : '取消预约失败'
  } finally {
    appointmentBusy.value = false
  }
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  selectedTaskId.value = ''
  selectedTechnicianId.value = ''
  void load()
})
watch(selectedTaskId, () => {
  void loadAppointments()
})
</script>

<template>
  <section data-testid="network-portal-tasks">
    <h2>本网点任务</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-tasks-table">
      <thead>
        <tr>
          <th>任务</th>
          <th>工单</th>
          <th>状态</th>
          <th>类型</th>
          <th>师傅</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.taskId">
          <td>{{ item.taskId }}</td>
          <td>{{ item.workOrderId }}</td>
          <td>{{ item.status ?? '—' }}</td>
          <td>{{ item.taskType ?? '—' }}</td>
          <td>{{ item.technicianId ?? '未指派' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 ACTIVE 责任任务</p>

    <form
      class="assign"
      data-testid="network-assign-technician-form"
      data-page-id="NETWORK.TECHNICIAN.ASSIGN"
      @submit.prevent="submitAssign"
    >
      <h3>指派师傅</h3>
      <p class="hint">调用 Network Portal 写命令；networkAssigneeId 由服务端强制为当前上下文网点。</p>
      <label>
        任务
        <select v-model="selectedTaskId" data-testid="assign-task-select" aria-label="assign task">
          <option disabled value="">选择任务</option>
          <option v-for="item in items" :key="item.taskId" :value="item.taskId">
            {{ item.taskId }}
          </option>
        </select>
      </label>
      <label>
        师傅
        <select
          v-model="selectedTechnicianId"
          data-testid="assign-technician-select"
          aria-label="assign technician"
        >
          <option disabled value="">选择师傅</option>
          <option
            v-for="tech in technicians"
            :key="tech.technicianProfileId"
            :value="tech.technicianProfileId"
          >
            {{ tech.displayName }}
          </option>
        </select>
      </label>
      <label>
        业务类型
        <input v-model="businessType" data-testid="assign-business-type" aria-label="business type" />
      </label>
      <button
        type="submit"
        data-testid="assign-technician-submit"
        :disabled="assignBusy || !props.networkContextId"
      >
        指派师傅
      </button>
      <p v-if="assignError" class="error" data-testid="assign-technician-error">{{ assignError }}</p>
      <p v-if="assignMessage" class="ok" data-testid="assign-technician-message">{{ assignMessage }}</p>
    </form>

    <form
      class="assign"
      data-testid="network-appointment-form"
      data-page-id="NETWORK.APPOINTMENT"
      @submit.prevent="submitPropose"
    >
      <h3>本网点预约</h3>
      <p class="hint">
        调用 Network Portal 预约 propose/confirm/reschedule/cancel；确认方固定为 NETWORK_MEMBER + 当前主体；
        改约/取消使用列表 If-Match 版本。
      </p>
      <label>
        任务
        <select
          v-model="selectedTaskId"
          data-testid="appointment-task-select"
          aria-label="appointment task"
        >
          <option disabled value="">选择任务</option>
          <option v-for="item in items" :key="item.taskId" :value="item.taskId">
            {{ item.taskId }}
          </option>
        </select>
      </label>
      <label>
        类型
        <select v-model="appointmentType" data-testid="appointment-type" aria-label="appointment type">
          <option value="SURVEY">SURVEY</option>
          <option value="INSTALLATION">INSTALLATION</option>
          <option value="REPAIR">REPAIR</option>
          <option value="CORRECTION">CORRECTION</option>
          <option value="SECOND_VISIT">SECOND_VISIT</option>
        </select>
      </label>
      <label>
        窗口开始
        <input v-model="windowStart" data-testid="appointment-window-start" aria-label="window start" />
      </label>
      <label>
        窗口结束
        <input v-model="windowEnd" data-testid="appointment-window-end" aria-label="window end" />
      </label>
      <label>
        地址引用
        <input v-model="addressRef" data-testid="appointment-address-ref" aria-label="address ref" />
      </label>
      <label>
        地址版本
        <input
          v-model="addressVersion"
          data-testid="appointment-address-version"
          aria-label="address version"
        />
      </label>
      <label>
        改约窗口开始
        <input
          v-model="rescheduleStart"
          data-testid="appointment-reschedule-start"
          aria-label="reschedule window start"
        />
      </label>
      <label>
        改约窗口结束
        <input
          v-model="rescheduleEnd"
          data-testid="appointment-reschedule-end"
          aria-label="reschedule window end"
        />
      </label>
      <label>
        改约原因
        <input
          v-model="rescheduleReason"
          data-testid="appointment-reschedule-reason"
          aria-label="reschedule reason"
        />
      </label>
      <label>
        取消原因
        <input
          v-model="cancelReason"
          data-testid="appointment-cancel-reason"
          aria-label="cancel reason"
        />
      </label>
      <button
        type="submit"
        data-testid="appointment-propose-submit"
        :disabled="appointmentBusy || !props.networkContextId"
      >
        提议预约
      </button>
      <ul data-testid="network-appointments-list" class="appointments">
        <li v-for="item in appointments" :key="item.appointmentId">
          <span>
            {{ item.appointmentId }} · {{ item.status }} · v{{ item.aggregateVersion }}
          </span>
          <span class="actions">
            <button
              v-if="item.status === 'PROPOSED'"
              type="button"
              data-testid="appointment-confirm-submit"
              :disabled="appointmentBusy"
              @click="submitConfirm(item)"
            >
              确认
            </button>
            <button
              v-if="item.status === 'CONFIRMED'"
              type="button"
              data-testid="appointment-reschedule-submit"
              :disabled="appointmentBusy"
              @click="submitReschedule(item)"
            >
              改约
            </button>
            <button
              v-if="item.status === 'PROPOSED' || item.status === 'CONFIRMED'"
              type="button"
              data-testid="appointment-cancel-submit"
              :disabled="appointmentBusy"
              @click="submitCancel(item)"
            >
              取消
            </button>
          </span>
        </li>
      </ul>
      <p v-if="appointmentError" class="error" data-testid="appointment-error">
        {{ appointmentError }}
      </p>
      <p v-if="appointmentMessage" class="ok" data-testid="appointment-message">
        {{ appointmentMessage }}
      </p>
    </form>
  </section>
</template>

<style scoped>
.assign {
  margin-top: 1.5rem;
  display: grid;
  gap: 0.75rem;
  max-width: 32rem;
}
.assign label {
  display: grid;
  gap: 0.25rem;
}
.hint {
  color: #555;
  font-size: 0.9rem;
}
.error {
  color: #a11;
}
.ok {
  color: #165;
}
.appointments {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.5rem;
}
.appointments li {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
</style>
