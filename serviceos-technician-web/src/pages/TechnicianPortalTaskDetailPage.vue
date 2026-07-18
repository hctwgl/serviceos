<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getTechnicianTaskDetail,
  checkInTechnicianVisit,
  interruptTechnicianVisit,
  type TechnicianPortalTaskDetail,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'

const props = defineProps<{ technicianContextId: string | null }>()
const route = useRoute()
const detail = ref<TechnicianPortalTaskDetail | null>(null)
const error = ref<string | null>(null)
const visitActionMessage = ref<string | null>(null)
const visitActionBusy = ref(false)
const interruptCode = ref('SITE_UNSAFE')
const interruptNote = ref('')

const confirmedAppointment = computed(() =>
  detail.value?.appointments.find((appointment) => appointment.status === 'CONFIRMED') ?? null,
)
const activeVisit = computed(() =>
  detail.value?.visits.find((visit) => visit.status === 'IN_PROGRESS') ?? null,
)

function browserDeviceId() {
  const key = 'serviceos-technician-web-device-id'
  const existing = sessionStorage.getItem(key)
  if (existing) return existing
  const next = `web-session-${crypto.randomUUID()}`
  sessionStorage.setItem(key, next)
  return next
}

function currentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位，请使用 iOS App 或允许定位的浏览器'))
      return
    }
    // H5 只做用户主动触发的一次定位，不持续跟踪，也不宣称具备原生可信度或后台能力。
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 15_000,
    })
  })
}

async function checkIn() {
  if (!props.technicianContextId || !confirmedAppointment.value || visitActionBusy.value) return
  visitActionBusy.value = true
  visitActionMessage.value = '正在获取一次定位…'
  try {
    const position = await currentPosition()
    const commandId = crypto.randomUUID()
    const receipt = await checkInTechnicianVisit(
      props.technicianContextId,
      confirmedAppointment.value.appointmentId,
      {
        capturedAt: new Date(position.timestamp).toISOString(),
        deviceCommandId: commandId,
        deviceId: browserDeviceId(),
        location: {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: Math.max(position.coords.accuracy, 0.1),
        },
      },
    )
    visitActionMessage.value = receipt.data.policyDecision === 'WARNING'
      ? `已到场，但位置策略提示 ${receipt.data.geofenceResult}`
      : '到场已由服务器确认'
    await load()
  } catch (err) {
    visitActionMessage.value = userFacingError(err, '到场记录失败，请检查定位权限或网络后重试')
  } finally {
    visitActionBusy.value = false
  }
}

async function interrupt() {
  if (!props.technicianContextId || !activeVisit.value || visitActionBusy.value) return
  visitActionBusy.value = true
  try {
    await interruptTechnicianVisit(
      props.technicianContextId,
      activeVisit.value.visitId,
      activeVisit.value.aggregateVersion,
      {
        capturedAt: new Date().toISOString(),
        exceptionCode: interruptCode.value,
        note: interruptNote.value.trim() || null,
        evidenceRefs: [],
      },
    )
    visitActionMessage.value = '无法施工已由服务器确认；未伪造任何资料上传'
    interruptNote.value = ''
    await load()
  } catch (err) {
    visitActionMessage.value = userFacingError(err, '无法施工记录失败，请刷新任务后重试')
  } finally {
    visitActionBusy.value = false
  }
}

async function load() {
  const taskId = String(route.params.id ?? '')
  if (!props.technicianContextId) {
    detail.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  if (!taskId) {
    detail.value = null
    error.value = '缺少 taskId'
    return
  }
  try {
    detail.value = await getTechnicianTaskDetail(props.technicianContextId, taskId)
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = userFacingError(err, '任务详情加载失败')
  }
}

onMounted(() => {
  void load()
})
watch([() => props.technicianContextId, () => route.params.id], () => {
  void load()
})
</script>

<template>
  <section data-testid="technician-portal-task-detail" data-page-id="TECHNICIAN.TASK.DETAIL">
    <header class="top">
      <div>
        <RouterLink to="/technician-portal/task-feed">← 返回任务 Feed</RouterLink>
        <h2>任务详情</h2>
        <p class="hint">M246：当前 ACTIVE 责任任务的在线非 PII 详情与协作历史；表单摘要另受 form.read 门禁。</p>
      </div>
      <button type="button" data-testid="technician-task-detail-refresh" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error" data-testid="technician-task-detail-error">{{ error }}</p>
    <template v-else-if="detail">
      <dl class="summary" data-testid="technician-task-detail-summary">
        <div><dt>taskId</dt><dd data-testid="technician-task-detail-task-id">{{ detail.taskId }}</dd></div>
        <div><dt>workOrderId</dt><dd>{{ detail.workOrderId }}</dd></div>
        <div><dt>projectId</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
        <div><dt>状态</dt><dd data-testid="technician-task-detail-status">{{ detail.taskStatus }}</dd></div>
        <div><dt>阶段</dt><dd>{{ detail.stageCode }}</dd></div>
        <div><dt>任务类型</dt><dd>{{ detail.taskType }} / {{ detail.taskKind }}</dd></div>
        <div><dt>业务类型</dt><dd>{{ detail.businessType ?? '—' }}</dd></div>
        <div><dt>执行保护</dt><dd>{{ detail.executionGuarded ? '已保护，暂不可执行' : '未保护' }}</dd></div>
        <div><dt>资源版本</dt><dd>{{ detail.resourceVersion }}</dd></div>
        <div><dt>asOf</dt><dd>{{ detail.asOf }}</dd></div>
      </dl>

      <section class="appointments">
        <div class="section-title">
          <h3>预约摘要</h3>
          <RouterLink
            :to="{ path: '/technician-portal/schedule', query: { taskId: detail.taskId } }"
            data-testid="technician-task-detail-schedule-link"
          >
            查看日程
          </RouterLink>
        </div>
        <table v-if="detail.appointments.length > 0" data-testid="technician-task-detail-appointments">
          <thead>
            <tr><th>类型</th><th>状态</th><th>开始</th><th>结束</th><th>时区</th></tr>
          </thead>
          <tbody>
            <tr v-for="appointment in detail.appointments" :key="appointment.appointmentId">
              <td>{{ appointment.type }}</td>
              <td>{{ appointment.status }}</td>
              <td>{{ appointment.windowStart ?? '—' }}</td>
              <td>{{ appointment.windowEnd ?? '—' }}</td>
              <td>{{ appointment.timezone ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else>暂无预约</p>
      </section>

      <section class="forms">
        <div class="section-title">
          <h3>表单提交</h3>
          <span>不含表单值与提交人</span>
        </div>
        <p v-if="detail.formSubmissions === null" data-testid="technician-task-detail-forms-hidden">
          当前上下文无表单读取权限
        </p>
        <table v-else-if="detail.formSubmissions.length > 0" data-testid="technician-task-detail-form-submissions">
          <thead>
            <tr><th>表单</th><th>版本</th><th>校验</th><th>错误</th><th>警告</th><th>提交时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="submission in detail.formSubmissions" :key="submission.submissionId">
              <td>{{ submission.formKey }}</td>
              <td>{{ submission.submissionVersion }}</td>
              <td>{{ submission.validationStatus }}</td>
              <td>{{ submission.errorCount }}</td>
              <td>{{ submission.warningCount }}</td>
              <td>{{ submission.submittedAt }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-form-submissions">暂无表单提交</p>
      </section>

      <section class="visits">
        <div class="section-title">
          <h3>上门历史</h3>
          <span>不含定位与设备明细</span>
        </div>
        <table v-if="detail.visits.length > 0" data-testid="technician-task-detail-visits">
          <thead>
            <tr><th>序次</th><th>状态</th><th>到场</th><th>围栏</th><th>策略</th><th>结果/异常</th><th>版本</th></tr>
          </thead>
          <tbody>
            <tr v-for="visit in detail.visits" :key="visit.visitId">
              <td>{{ visit.visitSequence }}</td>
              <td>{{ visit.status }}</td>
              <td>{{ visit.checkInCapturedAt }}</td>
              <td>{{ visit.geofenceResult }}</td>
              <td>{{ visit.policyDecision }}</td>
              <td>{{ visit.resultCode ?? visit.exceptionCode ?? '—' }}</td>
              <td>{{ visit.aggregateVersion }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-visits">暂无上门记录</p>

        <div class="visit-actions" data-testid="technician-visit-online-actions">
          <h4>在线现场操作</h4>
          <p class="hint">浏览器只在你点击时采集一次位置；不会后台定位，也不代表原生设备可信度。</p>
          <button
            v-if="confirmedAppointment && !activeVisit"
            type="button"
            :disabled="visitActionBusy || detail.executionGuarded"
            data-testid="technician-visit-check-in"
            @click="checkIn"
          >{{ visitActionBusy ? '处理中…' : '主动定位并签到' }}</button>

          <form v-if="activeVisit" class="interrupt-form" data-testid="technician-visit-interrupt-form" @submit.prevent="interrupt">
            <label>无法施工原因
              <select v-model="interruptCode" data-testid="technician-visit-interrupt-code">
                <option value="SITE_UNSAFE">现场不安全</option>
                <option value="MATERIAL_MISSING">物料缺失</option>
              </select>
            </label>
            <label>说明（可选）
              <textarea v-model="interruptNote" maxlength="500" data-testid="technician-visit-interrupt-note" />
            </label>
            <button type="submit" :disabled="visitActionBusy" data-testid="technician-visit-interrupt">
              {{ visitActionBusy ? '处理中…' : '确认无法施工' }}
            </button>
          </form>
          <p v-if="activeVisit" class="hint" data-testid="technician-visit-checkout-boundary">
            签退必须引用已完成现场操作；动态表单/资料尚未接入前，不会生成占位 operationRefs 或伪造完成。
          </p>
          <p v-if="visitActionMessage" class="action-message" role="status" data-testid="technician-visit-action-message">
            {{ visitActionMessage }}
          </p>
        </div>
      </section>

      <section class="contacts">
        <div class="section-title">
          <h3>联系历史</h3>
          <span>仅安全事实摘要</span>
        </div>
        <table v-if="detail.contactAttempts.length > 0" data-testid="technician-task-detail-contact-attempts">
          <thead>
            <tr><th>渠道</th><th>结果</th><th>开始</th><th>结束</th><th>下次联系</th></tr>
          </thead>
          <tbody>
            <tr v-for="attempt in detail.contactAttempts" :key="attempt.contactAttemptId">
              <td>{{ attempt.channel }}</td>
              <td>{{ attempt.resultCode }}</td>
              <td>{{ attempt.startedAt }}</td>
              <td>{{ attempt.endedAt }}</td>
              <td>{{ attempt.nextContactAt ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-contact-attempts">暂无联系记录</p>
      </section>

      <p class="boundary" data-testid="technician-task-detail-boundary">
        本切片不返回地址、联系人、联系对象引用、自由文本、录音引用、操作者标识、GPS、距离、设备、离线命令、现场备注、作业/资料引用、表单值、校验消息、提交人、资料文件、配置源码或离线工作包。
      </p>
    </template>
  </section>
</template>

<style scoped>
.top,
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.hint,
.boundary {
  color: #5b6573;
}
.error {
  color: #a11;
}
.visit-actions {
  margin-top: 1rem;
  padding: 1rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}
.interrupt-form {
  display: grid;
  gap: 0.75rem;
}
.interrupt-form label {
  display: grid;
  gap: 0.25rem;
}
.interrupt-form select,
.interrupt-form textarea {
  min-height: 44px;
  font: inherit;
}
.action-message {
  color: #155e3b;
  font-weight: 600;
}
.summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
  margin: 1rem 0;
}
.summary div {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.75rem;
}
.summary dt {
  color: #5b6573;
  font-size: 0.8rem;
}
.summary dd {
  margin: 0.3rem 0 0;
  overflow-wrap: anywhere;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.5rem;
  text-align: left;
}
.boundary {
  margin-top: 1rem;
  padding: 0.75rem;
  background: #f7f9fc;
}
</style>
