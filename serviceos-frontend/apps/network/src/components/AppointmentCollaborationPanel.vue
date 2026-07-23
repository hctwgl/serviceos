<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  confirmNetworkPortalAppointment,
  proposeNetworkPortalAppointment,
  recordNetworkPortalTaskContactAttempt,
  type NetworkPortalTaskItem,
  type NetworkPortalWorkspaceAppointmentSummary,
  type NetworkPortalWorkspaceContactAttemptSummary,
} from '../api/networkPortal'
import { getMe } from '../api/me'
import { statusLabel } from '../product/labels'
import { formatDateTime, safeProblemMessage } from '../product/labels'

const props = defineProps<{
  networkContextId: string
  tasks: NetworkPortalTaskItem[]
  appointments: NetworkPortalWorkspaceAppointmentSummary[] | null | undefined
  contactAttempts: NetworkPortalWorkspaceContactAttemptSummary[] | null | undefined
}>()

const emit = defineEmits<{
  changed: []
}>()

const selectedTaskId = ref('')
const windowStart = ref('')
const windowEnd = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const contactResult = ref('REACHED')
const contactChannel = ref('PHONE')

watch(
  () => props.tasks,
  (tasks) => {
    if (!selectedTaskId.value && tasks[0]) {
      selectedTaskId.value = tasks[0].taskId
    }
  },
  { immediate: true },
)

const latestAppointment = computed(() => {
  const list = props.appointments ?? []
  if (!selectedTaskId.value) return list[0] ?? null
  return list.find((item) => item.taskId === selectedTaskId.value) ?? list[0] ?? null
})

const taskContacts = computed(() => {
  const list = props.contactAttempts ?? []
  if (!selectedTaskId.value) return list
  return list.filter((item) => item.taskId === selectedTaskId.value)
})

function defaultWindows() {
  const start = new Date()
  start.setHours(start.getHours() + 24, 0, 0, 0)
  const end = new Date(start)
  end.setHours(end.getHours() + 2)
  windowStart.value = start.toISOString().slice(0, 16)
  windowEnd.value = end.toISOString().slice(0, 16)
}

watch(
  () => props.networkContextId,
  () => {
    defaultWindows()
    error.value = null
    message.value = null
  },
  { immediate: true },
)

function toIso(localValue: string) {
  const date = new Date(localValue)
  if (Number.isNaN(date.getTime())) {
    throw new Error('预约时间格式无效')
  }
  return date.toISOString()
}

async function propose() {
  if (!selectedTaskId.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const receipt = await proposeNetworkPortalAppointment(props.networkContextId, selectedTaskId.value, {
      type: 'SERVICE',
      window: {
        start: toIso(windowStart.value),
        end: toIso(windowEnd.value),
        timezone: 'Asia/Shanghai',
        estimatedDurationMinutes: 120,
      },
      // 地址引用由服务端策略解析；UI 不展示 addressRef 明文。
      addressRef: 'network-portal-workspace-address',
      addressVersion: '1',
    })
    message.value = `已提出预约草案（修订 ${receipt.data.revisionNo}），待确认后才生效`
    // 延迟刷新，避免父级重载立刻冲掉本地面反馈。
    window.setTimeout(() => emit('changed'), 0)
  } catch (err) {
    error.value = safeProblemMessage(err) || '提出预约失败'
  } finally {
    busy.value = false
  }
}

async function confirmLatest() {
  if (!latestAppointment.value) {
    error.value = '暂无可确认的预约'
    return
  }
  busy.value = true
  error.value = null
  message.value = null
  try {
    const me = await getMe()
    const receipt = await confirmNetworkPortalAppointment(
      props.networkContextId,
      latestAppointment.value.appointmentId,
      {
        confirmedPartyType: 'NETWORK_MEMBER',
        confirmedPartyRef: me.principalId,
        confirmationChannel: 'PHONE',
      },
      latestAppointment.value.aggregateVersion,
    )
    message.value = `预约已确认（修订 ${receipt.data.revisionNo}）`
    emit('changed')
  } catch (err) {
    error.value = safeProblemMessage(err) || '确认预约失败'
  } finally {
    busy.value = false
  }
}

async function recordContact() {
  if (!selectedTaskId.value) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const started = new Date()
    const ended = new Date(started.getTime() + 3 * 60_000)
    await recordNetworkPortalTaskContactAttempt(props.networkContextId, selectedTaskId.value, {
      channel: contactChannel.value,
      contactedPartyRef: 'CUSTOMER',
      startedAt: started.toISOString(),
      endedAt: ended.toISOString(),
      resultCode: contactResult.value,
    })
    message.value = '联系记录已追加'
    emit('changed')
  } catch (err) {
    error.value = safeProblemMessage(err) || '登记联系失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="appointment-panel" data-testid="workspace-appointment-collaboration">
    <header class="appointment-panel__head">
      <h3>预约协同</h3>
      <p class="muted">联系记录只追加；预约草案不等于已确认。</p>
    </header>

    <label class="field">
      <span>任务</span>
      <select v-model="selectedTaskId" data-testid="workspace-appointment-task">
        <option disabled value="">选择任务</option>
        <option v-for="task in tasks" :key="task.taskId" :value="task.taskId">
          {{ statusLabel(task.taskType || '') || '任务' }} · {{ statusLabel(task.status || '') }}
        </option>
      </select>
    </label>

    <div v-if="latestAppointment" class="current" data-testid="workspace-appointment-current">
      <strong>当前预约</strong>
      <p>
        {{ statusLabel(latestAppointment.type) }} · {{ statusLabel(latestAppointment.status) }} · 修订
        {{ latestAppointment.currentRevisionNo }}
      </p>
      <p v-if="latestAppointment.windowStart" class="muted">
        窗口 {{ formatDateTime(latestAppointment.windowStart) }}
        → {{ formatDateTime(latestAppointment.windowEnd || latestAppointment.windowStart) }}
      </p>
    </div>
    <p v-else-if="appointments" class="muted" data-testid="workspace-appointment-none">暂无预约，可提出时间窗口</p>
    <p v-else class="muted">预约摘要不可用（可能缺少 manageAppointment 能力）</p>

    <div class="grid">
      <label class="field">
        <span>建议开始</span>
        <input v-model="windowStart" type="datetime-local" data-testid="workspace-appointment-window-start" />
      </label>
      <label class="field">
        <span>建议结束</span>
        <input v-model="windowEnd" type="datetime-local" data-testid="workspace-appointment-window-end" />
      </label>
    </div>

    <div class="actions">
      <button type="button" class="primary" :disabled="busy || !selectedTaskId" data-testid="workspace-appointment-propose" @click="propose">
        {{ busy ? '处理中…' : '提出预约' }}
      </button>
      <button type="button" :disabled="busy || !latestAppointment" data-testid="workspace-appointment-confirm" @click="confirmLatest">
        确认预约
      </button>
    </div>

    <div class="contact">
      <h4>登记联系</h4>
      <div class="grid">
        <label class="field">
          <span>渠道</span>
          <select v-model="contactChannel" data-testid="workspace-contact-channel">
            <option value="PHONE">电话</option>
            <option value="SMS">短信</option>
            <option value="WECHAT">微信</option>
          </select>
        </label>
        <label class="field">
          <span>结果</span>
          <select v-model="contactResult" data-testid="workspace-contact-result">
            <option value="REACHED">已接通</option>
            <option value="NO_ANSWER">未接通</option>
            <option value="WRONG_NUMBER">空号/错号</option>
            <option value="REFUSED">用户拒绝</option>
          </select>
        </label>
      </div>
      <button type="button" :disabled="busy || !selectedTaskId" data-testid="workspace-contact-submit" @click="recordContact">
        追加联系记录
      </button>
      <ul v-if="taskContacts.length" class="history" data-testid="workspace-contact-history">
        <li v-for="(item, index) in taskContacts.slice(0, 5)" :key="index">
          {{ statusLabel(item.channel) }} · {{ statusLabel(item.resultCode) }} ·
          {{ formatDateTime(item.startedAt) }}
        </li>
      </ul>
    </div>

    <p v-if="error" class="error" data-testid="workspace-appointment-error">{{ error }}</p>
    <p v-if="message" class="ok" data-testid="workspace-appointment-message">{{ message }}</p>
  </section>
</template>

<style scoped>
.appointment-panel {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 14px 16px;
  display: grid;
  gap: 12px;
}
.appointment-panel__head h3,
.contact h4 {
  margin: 0 0 4px;
  font-size: 15px;
}
.muted {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
  margin: 0;
}
.field {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.field input,
.field select {
  border: 1px solid var(--sos-color-border-default);
  border-radius: 6px;
  padding: 8px 10px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: #fff;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
}
button.primary {
  background: var(--sos-primary-600);
  border-color: var(--sos-primary-600);
  color: #fff;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.current {
  border: 1px solid var(--sos-color-border-light);
  border-radius: 6px;
  padding: 10px;
  background: var(--sos-color-surface-subtle);
}
.history {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--sos-color-text-secondary);
}
.error {
  color: var(--sos-color-status-critical-fg);
  margin: 0;
}
.ok {
  color: var(--sos-color-status-success-fg);
  margin: 0;
}
@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
