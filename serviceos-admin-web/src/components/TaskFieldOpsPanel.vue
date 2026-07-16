<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  cancelAppointment,
  checkInVisit,
  checkOutVisit,
  confirmAppointment,
  interruptVisit,
  listTaskAppointments,
  listTaskContactAttempts,
  listWorkOrderVisits,
  markAppointmentNoShow,
  proposeAppointment,
  recordTaskContactAttempt,
  rescheduleAppointment,
  type Appointment,
  type ContactAttempt,
  type Visit,
} from '../api/appointments'
import { newIdempotencyKey } from '../api/client'
import QueueTable from '../pages/QueueTable.vue'

const props = defineProps<{ taskId: string; workOrderId: string | null }>()

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const appointments = ref<Appointment[]>([])
const contacts = ref<ContactAttempt[]>([])
const visits = ref<Visit[]>([])

const apptType = ref<'SURVEY' | 'INSTALLATION' | 'REPAIR' | 'CORRECTION' | 'SECOND_VISIT'>('INSTALLATION')
const windowStart = ref('')
const windowEnd = ref('')
const timezone = ref('Asia/Shanghai')
const durationMinutes = ref(60)
const addressRef = ref('address://site-1')
const addressVersion = ref('v1')
const selectedAppointmentId = ref('')
const confirmPartyType = ref('CUSTOMER')
const confirmPartyRef = ref('party://customer-1')
const confirmChannel = ref('PHONE')
const cancelReason = ref('CUSTOMER_CANCELLED')
const rescheduleReason = ref('CUSTOMER_REQUEST')
const noShowPartyType = ref('CUSTOMER')
const noShowPartyRef = ref('party://customer-1')
const noShowReason = ref('CUSTOMER_NO_SHOW')
const noShowEvidenceRefs = ref('evidence://manual-1')
const interruptCode = ref('SITE_BLOCKED')
const interruptNote = ref('')
const interruptEvidenceRefs = ref('evidence://manual-1')

const contactChannel = ref('PHONE')
const contactedPartyRef = ref('party://customer-1')
const contactResult = ref<'CONNECTED' | 'NO_ANSWER' | 'BUSY' | 'WRONG_NUMBER' | 'USER_REQUESTED_LATER' | 'INVALID_CONTACT'>('CONNECTED')
const contactNote = ref('')

const latitude = ref(31.23)
const longitude = ref(121.47)
const accuracyMeters = ref(25)
const checkOutResult = ref('COMPLETED_OK')
const operationRefs = ref('op://field-1')
const selectedVisitId = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    const [apptList, contactList] = await Promise.all([
      listTaskAppointments(props.taskId),
      listTaskContactAttempts(props.taskId),
    ])
    appointments.value = apptList
    contacts.value = contactList
    if (!selectedAppointmentId.value && apptList[0]) {
      selectedAppointmentId.value = apptList[0].appointmentId
    }
    if (props.workOrderId) {
      visits.value = await listWorkOrderVisits(props.workOrderId)
      const inProgress = visits.value.find((v) => v.status === 'IN_PROGRESS')
      if (inProgress) selectedVisitId.value = inProgress.visitId
    } else {
      visits.value = []
    }
    if (!windowStart.value) {
      const start = new Date(Date.now() + 60 * 60 * 1000)
      const end = new Date(start.getTime() + 60 * 60 * 1000)
      windowStart.value = start.toISOString()
      windowEnd.value = end.toISOString()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载预约/联系失败'
  } finally {
    loading.value = false
  }
}

function selectedAppointment(): Appointment | undefined {
  return appointments.value.find((item) => item.appointmentId === selectedAppointmentId.value)
}

async function propose() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const receipt = await proposeAppointment(props.taskId, {
      type: apptType.value,
      window: {
        start: windowStart.value,
        end: windowEnd.value,
        timezone: timezone.value,
        estimatedDurationMinutes: Number(durationMinutes.value),
      },
      addressRef: addressRef.value.trim(),
      addressVersion: addressVersion.value.trim(),
    })
    selectedAppointmentId.value = receipt.data.appointmentId
    message.value = `已提议预约 ${receipt.data.appointmentId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '提议预约失败'
  } finally {
    busy.value = false
  }
}

async function confirm() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const appt = selectedAppointment()
    if (!appt) throw new Error('需要选择预约')
    const receipt = await confirmAppointment(appt.appointmentId, appt.aggregateVersion, {
      confirmedPartyType: confirmPartyType.value.trim(),
      confirmedPartyRef: confirmPartyRef.value.trim(),
      confirmationChannel: confirmChannel.value.trim(),
    })
    message.value = `已确认预约 ${receipt.data.appointmentId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '确认预约失败'
  } finally {
    busy.value = false
  }
}

async function cancel() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const appt = selectedAppointment()
    if (!appt) throw new Error('需要选择预约')
    const receipt = await cancelAppointment(appt.appointmentId, appt.aggregateVersion, {
      reasonCode: cancelReason.value.trim(),
    })
    message.value = `已取消预约 ${receipt.data.appointmentId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '取消预约失败'
  } finally {
    busy.value = false
  }
}

async function reschedule() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const appt = selectedAppointment()
    if (!appt) throw new Error('需要选择预约')
    const receipt = await rescheduleAppointment(appt.appointmentId, appt.aggregateVersion, {
      newWindow: {
        start: windowStart.value,
        end: windowEnd.value,
        timezone: timezone.value,
        estimatedDurationMinutes: Number(durationMinutes.value),
      },
      reasonCode: rescheduleReason.value.trim(),
    })
    message.value = `已改约 ${receipt.data.appointmentId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '改约失败'
  } finally {
    busy.value = false
  }
}

async function markNoShow() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const appt = selectedAppointment()
    if (!appt) throw new Error('需要选择预约')
    const refs = noShowEvidenceRefs.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const receipt = await markAppointmentNoShow(appt.appointmentId, appt.aggregateVersion, {
      noShowPartyType: noShowPartyType.value.trim(),
      noShowPartyRef: noShowPartyRef.value.trim(),
      reasonCode: noShowReason.value.trim(),
      evidenceRefs: refs,
    })
    message.value = `已标记爽约 ${receipt.data.appointmentId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '标记爽约失败'
  } finally {
    busy.value = false
  }
}

async function recordContact() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const startedAt = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    const endedAt = new Date().toISOString()
    const created = await recordTaskContactAttempt(props.taskId, {
      channel: contactChannel.value.trim(),
      contactedPartyRef: contactedPartyRef.value.trim(),
      startedAt,
      endedAt,
      resultCode: contactResult.value,
      note: contactNote.value.trim() || null,
    })
    message.value = `已记录联系 ${created.data.contactAttemptId} / ${created.data.resultCode}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '记录联系失败'
  } finally {
    busy.value = false
  }
}

async function doCheckIn() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!selectedAppointmentId.value) throw new Error('需要选择预约')
    const deviceCommandId = newIdempotencyKey('checkin')
    const receipt = await checkInVisit(selectedAppointmentId.value, {
      capturedAt: new Date().toISOString(),
      deviceCommandId,
      deviceId: 'admin-web-simulator',
      location: {
        latitude: Number(latitude.value),
        longitude: Number(longitude.value),
        accuracyMeters: Number(accuracyMeters.value),
      },
      offline: false,
    })
    selectedVisitId.value = receipt.data.visitId
    message.value = `签到 Visit ${receipt.data.visitId} / ${receipt.data.status} / ${receipt.data.geofenceResult}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '签到失败'
  } finally {
    busy.value = false
  }
}

async function doCheckOut() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const visit = visits.value.find((item) => item.visitId === selectedVisitId.value)
    if (!visit) throw new Error('需要选择 IN_PROGRESS Visit')
    const refs = operationRefs.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    if (refs.length === 0) throw new Error('需要 operationRefs')
    const receipt = await checkOutVisit(visit.visitId, visit.aggregateVersion, {
      capturedAt: new Date().toISOString(),
      resultCode: checkOutResult.value.trim(),
      operationRefs: refs,
    })
    message.value = `签退 Visit ${receipt.data.visitId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '签退失败'
  } finally {
    busy.value = false
  }
}

async function doInterrupt() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const visit = visits.value.find((item) => item.visitId === selectedVisitId.value)
    if (!visit) throw new Error('需要选择 IN_PROGRESS Visit')
    const refs = interruptEvidenceRefs.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const receipt = await interruptVisit(visit.visitId, visit.aggregateVersion, {
      capturedAt: new Date().toISOString(),
      exceptionCode: interruptCode.value.trim(),
      note: interruptNote.value.trim() || null,
      evidenceRefs: refs,
    })
    message.value = `中断 Visit ${receipt.data.visitId} / ${receipt.data.status}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '中断上门失败'
  } finally {
    busy.value = false
  }
}

const appointmentRows = computed(() =>
  appointments.value.map((item) => ({
    appointmentId: item.appointmentId,
    type: item.type,
    status: item.status,
    aggregateVersion: item.aggregateVersion,
    allowedActions: item.allowedActions.join(','),
    technicianId: item.technicianId,
  })),
)
const contactRows = computed(() =>
  contacts.value.map((item) => ({
    contactAttemptId: item.contactAttemptId,
    channel: item.channel,
    resultCode: item.resultCode,
    contactedPartyRef: item.contactedPartyRef,
    startedAt: item.startedAt,
    endedAt: item.endedAt,
  })),
)
const visitRows = computed(() =>
  visits.value.map((item) => ({
    visitId: item.visitId,
    appointmentId: item.appointmentId,
    status: item.status,
    visitSequence: item.visitSequence,
    aggregateVersion: item.aggregateVersion,
    technicianId: item.technicianId,
  })),
)

watch(
  () => [props.taskId, props.workOrderId],
  () => {
    void load()
  },
)
onMounted(() => {
  void load()
})
</script>

<template>
  <section class="panel">
    <header class="top">
      <h3>联系 / 预约 / 上门</h3>
      <button type="button" :disabled="loading || busy" @click="load">刷新</button>
    </header>
    <p class="meta">运营最小切片：记录联系、提议/确认/取消预约、模拟签到签退。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-else-if="loading">加载中…</p>

    <QueueTable
      title="联系历史"
      :columns="['contactAttemptId', 'channel', 'resultCode', 'contactedPartyRef', 'startedAt', 'endedAt']"
      :rows="contactRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article class="card">
      <h4>记录联系</h4>
      <label>channel<input v-model="contactChannel" /></label>
      <label>contactedPartyRef<input v-model="contactedPartyRef" /></label>
      <label>
        resultCode
        <select v-model="contactResult">
          <option value="CONNECTED">CONNECTED</option>
          <option value="NO_ANSWER">NO_ANSWER</option>
          <option value="BUSY">BUSY</option>
          <option value="WRONG_NUMBER">WRONG_NUMBER</option>
          <option value="USER_REQUESTED_LATER">USER_REQUESTED_LATER</option>
          <option value="INVALID_CONTACT">INVALID_CONTACT</option>
        </select>
      </label>
      <label>note<input v-model="contactNote" /></label>
      <button type="button" :disabled="busy" @click="recordContact">recordContactAttempt</button>
    </article>

    <QueueTable
      title="预约"
      :columns="['appointmentId', 'type', 'status', 'aggregateVersion', 'allowedActions', 'technicianId']"
      :rows="appointmentRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article class="card">
      <h4>提议预约</h4>
      <label>
        type
        <select v-model="apptType">
          <option value="SURVEY">SURVEY</option>
          <option value="INSTALLATION">INSTALLATION</option>
          <option value="REPAIR">REPAIR</option>
          <option value="CORRECTION">CORRECTION</option>
          <option value="SECOND_VISIT">SECOND_VISIT</option>
        </select>
      </label>
      <label>window.start<input v-model="windowStart" /></label>
      <label>window.end<input v-model="windowEnd" /></label>
      <label>timezone<input v-model="timezone" /></label>
      <label>estimatedDurationMinutes<input v-model.number="durationMinutes" type="number" min="1" /></label>
      <label>addressRef<input v-model="addressRef" /></label>
      <label>addressVersion<input v-model="addressVersion" /></label>
      <button type="button" :disabled="busy" @click="propose">proposeAppointment</button>
    </article>

    <article v-if="appointments.length" class="card">
      <h4>确认 / 改约 / 取消 / 爽约</h4>
      <label>
        appointmentId
        <select v-model="selectedAppointmentId">
          <option v-for="item in appointments" :key="item.appointmentId" :value="item.appointmentId">
            {{ item.status }} / {{ item.appointmentId }}
          </option>
        </select>
      </label>
      <label>confirmedPartyType<input v-model="confirmPartyType" /></label>
      <label>confirmedPartyRef<input v-model="confirmPartyRef" /></label>
      <label>confirmationChannel<input v-model="confirmChannel" /></label>
      <label>reschedule reasonCode<input v-model="rescheduleReason" /></label>
      <label>cancel reasonCode<input v-model="cancelReason" /></label>
      <label>noShowPartyType<input v-model="noShowPartyType" /></label>
      <label>noShowPartyRef<input v-model="noShowPartyRef" /></label>
      <label>noShow reasonCode<input v-model="noShowReason" /></label>
      <label>noShow evidenceRefs<input v-model="noShowEvidenceRefs" /></label>
      <div class="actions">
        <button type="button" :disabled="busy" @click="confirm">confirm</button>
        <button type="button" :disabled="busy" @click="reschedule">reschedule</button>
        <button type="button" :disabled="busy" @click="cancel">cancel</button>
        <button type="button" :disabled="busy" @click="markNoShow">mark-no-show</button>
      </div>
    </article>

    <QueueTable
      title="上门 Visit"
      :columns="['visitId', 'appointmentId', 'status', 'visitSequence', 'aggregateVersion', 'technicianId']"
      :rows="visitRows"
      :loading="false"
      :error="null"
      :next-cursor="null"
      @refresh="load"
      @next="() => undefined"
    />

    <article class="card">
      <h4>签到 / 签退</h4>
      <label>
        appointmentId（签到）
        <select v-model="selectedAppointmentId">
          <option v-for="item in appointments" :key="item.appointmentId" :value="item.appointmentId">
            {{ item.status }} / {{ item.appointmentId }}
          </option>
        </select>
      </label>
      <label>latitude<input v-model.number="latitude" type="number" step="0.0001" /></label>
      <label>longitude<input v-model.number="longitude" type="number" step="0.0001" /></label>
      <label>accuracyMeters<input v-model.number="accuracyMeters" type="number" min="1" /></label>
      <label>
        visitId（签退）
        <select v-model="selectedVisitId">
          <option v-for="item in visits" :key="item.visitId" :value="item.visitId">
            {{ item.status }} / {{ item.visitId }}
          </option>
        </select>
      </label>
      <label>checkOut resultCode<input v-model="checkOutResult" /></label>
      <label>operationRefs<input v-model="operationRefs" /></label>
      <label>interrupt exceptionCode<input v-model="interruptCode" /></label>
      <label>interrupt note<input v-model="interruptNote" /></label>
      <label>interrupt evidenceRefs<input v-model="interruptEvidenceRefs" /></label>
      <div class="actions">
        <button type="button" :disabled="busy" @click="doCheckIn">check-in</button>
        <button type="button" :disabled="busy" @click="doCheckOut">check-out</button>
        <button type="button" :disabled="busy" @click="doInterrupt">interrupt</button>
      </div>
      <p v-if="!workOrderId" class="meta">缺少 workOrderId 时无法加载 Visit 列表，仍可对预约执行签到。</p>
    </article>
  </section>
</template>

<style scoped>
.panel { display: grid; gap: 1rem; }
.top { display: flex; justify-content: space-between; align-items: center; }
.top h3 { margin: 0; }
.meta { margin: 0; color: #627d98; font-size: .85rem; word-break: break-all; }
.card { background: #fff; border-radius: 12px; padding: 1rem 1.15rem; box-shadow: 0 1px 3px rgb(16 42 67 / 8%); display: grid; gap: .55rem; }
label { display: grid; gap: .25rem; font-size: .85rem; color: #486581; }
input, select, button { border: 1px solid #bcccdc; border-radius: 6px; padding: .4rem .65rem; font-family: ui-monospace, monospace; }
button { background: #243b53; color: #fff; border-color: #243b53; cursor: pointer; font-family: inherit; }
.actions { display: flex; gap: .5rem; flex-wrap: wrap; }
.error { color: #9b1c1c; }
.ok { color: #054e31; }
</style>
