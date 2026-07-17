<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  assignNetworkPortalTechnician,
  listNetworkPortalTasks,
  listNetworkPortalTechnicians,
  type NetworkPortalTaskItem,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTaskItem[]>([])
const technicians = ref<NetworkPortalTechnicianItem[]>([])
const error = ref<string | null>(null)
const selectedTaskId = ref('')
const selectedTechnicianId = ref('')
const businessType = ref('INSTALLATION')
const assignBusy = ref(false)
const assignError = ref<string | null>(null)
const assignMessage = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    technicians.value = []
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

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  selectedTaskId.value = ''
  selectedTechnicianId.value = ''
  void load()
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
</style>
