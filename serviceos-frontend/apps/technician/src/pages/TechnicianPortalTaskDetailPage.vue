<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getTechnicianTaskDetail,
  claimMainTask,
  startMainTask,
  checkInTechnicianVisit,
  interruptTechnicianVisit,
  listTechnicianTaskForms,
  submitTechnicianTaskForm,
  listTechnicianTaskEvidenceSlots,
  listTechnicianTaskEvidenceItems,
  beginTechnicianEvidenceUpload,
  putTechnicianEvidenceUpload,
  finalizeTechnicianEvidenceUpload,
  createTechnicianTaskEvidenceSetSnapshot,
  completeTechnicianTask,
  sha256Hex,
  type TechnicianEvidenceItem,
  type TechnicianEvidenceSlot,
  type TechnicianPortalTaskDetail,
  type TechnicianTaskForm,
  type TechnicianTaskFormField,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'
import { formatDateTime } from '../product/labels'
import {
  coerceFormValuesForExpr,
  evaluateServiceOsExprV1,
  expressionSource,
  ExpressionEvaluationError,
} from '../expression/serviceosExprV1Evaluate'

const props = defineProps<{ technicianContextId: string | null }>()
const route = useRoute()
const detail = ref<TechnicianPortalTaskDetail | null>(null)
const error = ref<string | null>(null)
const visitActionMessage = ref<string | null>(null)
const visitActionBusy = ref(false)
const interruptCode = ref('SITE_UNSAFE')
const interruptNote = ref('')
const taskForms = ref<TechnicianTaskForm[]>([])
const formValues = ref<Record<string, string | boolean>>({})
const formLoading = ref(false)
const formSubmitting = ref(false)
const formMessage = ref<string | null>(null)
const formIssues = ref<Array<{ fieldKey: string; code: string; message: string }>>([])
const evidenceSlots = ref<TechnicianEvidenceSlot[]>([])
const evidenceItems = ref<TechnicianEvidenceItem[]>([])
const selectedEvidenceFiles = ref<Record<string, File | undefined>>({})
const evidenceLoading = ref(false)
const evidenceUploadingSlotId = ref<string | null>(null)
const evidenceMessage = ref<string | null>(null)
const taskSubmitting = ref(false)
const taskSubmissionMessage = ref<string | null>(null)
const taskClaimStarting = ref(false)
const taskClaimMessage = ref<string | null>(null)

const supportedFormTypes = new Set(['STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'])
const activeForm = computed(() => taskForms.value[0] ?? null)
const formFields = computed(() => activeForm.value?.definition.sections.flatMap((section) => section.fields) ?? [])

function requireServiceOsExpr(raw: unknown, owner: string, reasons: Set<string>): void {
  if (!expressionSource(raw)) {
    reasons.add(`${owner} 条件语言不是 SERVICEOS_EXPR_V1`)
  }
}

const unsupportedFormReasons = computed(() => {
  const form = activeForm.value
  if (!form) return []
  const reasons = new Set<string>()
  for (const rule of form.definition.validationRules ?? []) {
    const assertExpr = (rule as { assert?: unknown }).assert
    if (!assertExpr) {
      reasons.add('跨字段规则缺少 assert')
      continue
    }
    requireServiceOsExpr(assertExpr, `规则 ${(rule as { ruleKey?: string }).ruleKey ?? '?'}`, reasons)
  }
  for (const section of form.definition.sections) {
    if (section.visibility) {
      requireServiceOsExpr(section.visibility, `分区 ${section.sectionKey}`, reasons)
    }
    for (const field of section.fields) {
      if (!supportedFormTypes.has(field.dataType)) {
        reasons.add(`字段类型 ${field.dataType} 尚未接入`)
      }
      if (field.editableWhen || field.defaultExpression) {
        // 后端 ConfigurationAssetSchemaValidator / FormValueValidator 仍拒绝发布与提交
        reasons.add('字段 editableWhen/默认值运行时尚未接受')
      }
      for (const cond of [field.visibleWhen, field.requiredWhen]) {
        if (!cond) continue
        requireServiceOsExpr(cond, `字段 ${field.fieldKey}`, reasons)
      }
      if (field.optionsRef || (field.validators?.length ?? 0) > 0) {
        reasons.add('选项或扩展校验器尚未接入')
      }
    }
  }
  return [...reasons]
})

function taskExprPaths(): Partial<Record<string, string>> {
  const task = detail.value
  if (!task) return {}
  return {
    'workOrder.clientCode': task.clientCode,
    'workOrder.brandCode': task.brandCode,
    'workOrder.serviceProductCode': task.serviceProductCode,
    'region.provinceCode': task.provinceCode,
    'region.cityCode': task.cityCode,
    'region.districtCode': task.districtCode,
    'task.stageCode': task.stageCode,
    'task.taskType': task.taskType,
  }
}

type CondEval = { ok: true; value: boolean } | { ok: false; error: string }

function evalCondition(raw: unknown | undefined, formValueMap: Record<string, unknown>): CondEval {
  if (!raw) return { ok: true, value: true }
  const source = expressionSource(raw)
  if (!source) return { ok: false, error: '条件不是 SERVICEOS_EXPR_V1' }
  try {
    return {
      ok: true,
      value: evaluateServiceOsExprV1(source, {
        formValues: formValueMap,
        paths: taskExprPaths(),
      }),
    }
  } catch (err) {
    const message =
      err instanceof ExpressionEvaluationError
        ? err.message
        : err instanceof Error
          ? err.message
          : '条件求值失败'
    return { ok: false, error: message }
  }
}

/** 当前草稿下各分区/字段显隐、必填与跨字段规则；求值失败失败关闭并阻断提交。 */
const formConditionState = computed(() => {
  const form = activeForm.value
  const errors: string[] = []
  const ruleFailures: string[] = []
  const sectionVisible = new Map<string, boolean>()
  const fieldVisible = new Map<string, boolean>()
  const fieldRequired = new Map<string, boolean>()
  if (!form || unsupportedFormReasons.value.length > 0) {
    return { errors, ruleFailures, sectionVisible, fieldVisible, fieldRequired }
  }
  let formValueMap: Record<string, unknown> = {}
  try {
    formValueMap = coerceFormValuesForExpr(formFields.value, formValues.value).values
  } catch (err) {
    errors.push(err instanceof Error ? err.message : '表单值无法用于条件求值')
    return { errors, ruleFailures, sectionVisible, fieldVisible, fieldRequired }
  }
  for (const section of form.definition.sections) {
    const sectionEval = evalCondition(section.visibility, formValueMap)
    if (!sectionEval.ok) {
      errors.push(`分区 ${section.sectionKey}：${sectionEval.error}`)
      sectionVisible.set(section.sectionKey, false)
      for (const field of section.fields) {
        fieldVisible.set(field.fieldKey, false)
        fieldRequired.set(field.fieldKey, false)
      }
      continue
    }
    sectionVisible.set(section.sectionKey, sectionEval.value)
    for (const field of section.fields) {
      if (!sectionEval.value) {
        fieldVisible.set(field.fieldKey, false)
        fieldRequired.set(field.fieldKey, false)
        continue
      }
      const visibleEval = evalCondition(field.visibleWhen, formValueMap)
      if (!visibleEval.ok) {
        errors.push(`字段 ${field.fieldKey} visibleWhen：${visibleEval.error}`)
        fieldVisible.set(field.fieldKey, false)
        fieldRequired.set(field.fieldKey, false)
        continue
      }
      const visible = visibleEval.value
      fieldVisible.set(field.fieldKey, visible)
      if (!visible) {
        fieldRequired.set(field.fieldKey, false)
        continue
      }
      if (!field.requiredWhen) {
        fieldRequired.set(field.fieldKey, Boolean(field.required))
        continue
      }
      const requiredEval = evalCondition(field.requiredWhen, formValueMap)
      if (!requiredEval.ok) {
        errors.push(`字段 ${field.fieldKey} requiredWhen：${requiredEval.error}`)
        // 无法判定必填时按必填处理，并阻断提交
        fieldRequired.set(field.fieldKey, true)
        continue
      }
      fieldRequired.set(field.fieldKey, Boolean(field.required) || requiredEval.value)
    }
  }
  for (const rule of form.definition.validationRules ?? []) {
    const ruleKey = String((rule as { ruleKey?: unknown }).ruleKey ?? 'rule')
    const message = String((rule as { message?: unknown }).message ?? '表单跨字段规则未通过')
    const assertEval = evalCondition((rule as { assert?: unknown }).assert, formValueMap)
    if (!assertEval.ok) {
      errors.push(`规则 ${ruleKey}：${assertEval.error}`)
      continue
    }
    if (!assertEval.value) {
      ruleFailures.push(`${ruleKey}：${message}`)
    }
  }
  return { errors, ruleFailures, sectionVisible, fieldVisible, fieldRequired }
})

function isSectionVisible(sectionKey: string): boolean {
  return formConditionState.value.sectionVisible.get(sectionKey) !== false
}

function isFieldVisible(fieldKey: string): boolean {
  return formConditionState.value.fieldVisible.get(fieldKey) !== false
}

function isFieldRequired(field: TechnicianTaskFormField): boolean {
  return formConditionState.value.fieldRequired.get(field.fieldKey) === true
}

const confirmedAppointment = computed(() =>
  detail.value?.appointments.find((appointment) => appointment.status === 'CONFIRMED') ?? null,
)
const activeVisit = computed(() =>
  detail.value?.visits.find((visit) => visit.status === 'IN_PROGRESS') ?? null,
)
const hasCheckedIn = computed(
  () =>
    Boolean(activeVisit.value) ||
    (detail.value?.visits.some((visit) => visit.checkInCapturedAt) ?? false),
)
const hasValidatedForm = computed(() => {
  const submissions = detail.value?.formSubmissions
  if (submissions === null) return false
  if (!activeForm.value) return true
  return (submissions ?? []).some((row) => row.validationStatus === 'VALIDATED')
})
const requiredEvidenceReady = computed(() => {
  if (evidenceSlots.value.length === 0) return true
  return evidenceSlots.value.every((slot) => {
    if (!slot.required) return true
    const count = evidenceItems.value
      // Item 生命周期状态为 OPEN 等（非 ACTIVE）；可用性由是否存在 VALIDATED Revision 判定。
      .filter((item) => item.evidenceSlotId === slot.slotId)
      .filter((item) => (item.revisions ?? []).some((revision) => revision.status === 'VALIDATED'))
      .length
    return count >= Math.max(slot.minCount ?? 0, 1)
  })
})

type WorkStepKey = 'appointment' | 'checkin' | 'form' | 'evidence' | 'presubmit'
type WorkStep = { key: WorkStepKey; label: string; status: 'done' | 'current' | 'upcoming' | 'blocked' }

const workSteps = computed<WorkStep[]>(() => {
  const guarded = Boolean(detail.value?.executionGuarded)
  const appointmentDone = (detail.value?.appointments.length ?? 0) > 0
  const checkinDone = hasCheckedIn.value
  const formDone = hasValidatedForm.value
  const evidenceDone = requiredEvidenceReady.value
  const steps: WorkStep[] = [
    {
      key: 'appointment',
      label: '预约确认',
      status: appointmentDone ? 'done' : guarded ? 'blocked' : 'current',
    },
    {
      key: 'checkin',
      label: '到场签到',
      status: checkinDone ? 'done' : appointmentDone ? (guarded ? 'blocked' : 'current') : 'upcoming',
    },
    {
      key: 'form',
      label: '现场表单',
      status: formDone
        ? 'done'
        : checkinDone
          ? guarded || unsupportedFormReasons.value.length > 0
            ? 'blocked'
            : 'current'
          : 'upcoming',
    },
    {
      key: 'evidence',
      label: '资料上传',
      status: evidenceDone
        ? 'done'
        : formDone
          ? guarded
            ? 'blocked'
            : 'current'
          : 'upcoming',
    },
    {
      key: 'presubmit',
      label: '提交前检查',
      status:
        appointmentDone && checkinDone && formDone && evidenceDone
          ? guarded
            ? 'blocked'
            : 'current'
          : 'upcoming',
    },
  ]
  let sawCurrent = false
  return steps.map((step) => {
    if (step.status === 'current') {
      if (sawCurrent) return { ...step, status: 'upcoming' as const }
      sawCurrent = true
      return step
    }
    return step
  })
})

const currentWorkStep = computed(
  () => workSteps.value.find((step) => step.status === 'current') ?? workSteps.value.at(-1)!,
)

const preSubmitChecks = computed(() => [
  {
    key: 'appointment',
    label: '已有预约安排',
    ok: (detail.value?.appointments.length ?? 0) > 0,
  },
  {
    key: 'checkin',
    label: '已完成到场签到',
    ok: hasCheckedIn.value,
  },
  {
    key: 'form',
    label: activeForm.value ? '表单已通过服务器校验' : '无锁定表单（跳过）',
    ok: hasValidatedForm.value,
  },
  {
    key: 'evidence',
    label: '必需资料均已 VALIDATED',
    ok: requiredEvidenceReady.value,
  },
  {
    key: 'running',
    label: '任务处于可执行状态',
    ok: detail.value?.taskStatus === 'RUNNING' && !detail.value.executionGuarded,
  },
])

const canCompleteTask = computed(() => preSubmitChecks.value.every((item) => item.ok))

const stickyAction = computed(() => {
  const step = currentWorkStep.value.key
  if (detail.value?.executionGuarded) {
    return { kind: 'disabled' as const, label: '任务已保护，暂不可执行', disabled: true }
  }
  if (step === 'appointment') {
    return {
      kind: 'link' as const,
      label: '查看日程 / 预约',
      to: '/technician-portal/schedule',
      disabled: false,
    }
  }
  if (step === 'checkin') {
    return {
      kind: 'checkin' as const,
      label: visitActionBusy.value ? '签到处理中…' : '到场签到',
      disabled: !confirmedAppointment.value || visitActionBusy.value,
    }
  }
  if (step === 'form') {
    return {
      kind: 'anchor' as const,
      label: '去填写表单',
      target: 'technician-online-form',
      disabled: false,
    }
  }
  if (step === 'evidence') {
    return {
      kind: 'anchor' as const,
      label: '去上传资料',
      target: 'technician-online-evidence',
      disabled: false,
    }
  }
  return {
    kind: 'complete' as const,
    label: taskSubmitting.value ? '服务器复核并完成中…' : '冻结资料并完成任务',
    disabled: !canCompleteTask.value || taskSubmitting.value,
  }
})

function scrollToTarget(target: string) {
  document.querySelector(`[data-testid="${target}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function onStickyAction() {
  const action = stickyAction.value
  if (action.kind === 'checkin') {
    await checkIn()
    return
  }
  if (action.kind === 'anchor') {
    scrollToTarget(action.target)
    return
  }
  if (action.kind === 'complete') {
    await completeTask()
  }
}

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

function inputType(field: TechnicianTaskFormField) {
  if (field.dataType === 'INTEGER' || field.dataType === 'DECIMAL') return 'number'
  if (field.dataType === 'DATE') return 'date'
  if (field.dataType === 'DATETIME') return 'datetime-local'
  return 'text'
}

function setTextValue(fieldKey: string, event: Event) {
  formValues.value[fieldKey] = (event.target as HTMLInputElement | HTMLTextAreaElement).value
}

function submissionValues(): Record<string, unknown> | null {
  const values: Record<string, unknown> = {}
  const missing: string[] = []
  for (const field of formFields.value) {
    if (!isFieldVisible(field.fieldKey)) {
      continue
    }
    const raw = formValues.value[field.fieldKey]
    if (field.dataType === 'BOOLEAN') {
      values[field.fieldKey] = raw === true
      continue
    }
    const text = typeof raw === 'string' ? raw.trim() : ''
    if (!text) {
      if (isFieldRequired(field)) missing.push(field.label)
      continue
    }
    if (field.dataType === 'INTEGER') {
      if (!/^-?\d+$/.test(text)) {
        formMessage.value = `${field.label} 必须是整数`
        return null
      }
      values[field.fieldKey] = Number(text)
    } else if (field.dataType === 'DECIMAL') {
      const number = Number(text)
      if (!Number.isFinite(number)) {
        formMessage.value = `${field.label} 必须是数字`
        return null
      }
      values[field.fieldKey] = number
    } else if (field.dataType === 'DATETIME') {
      values[field.fieldKey] = new Date(text).toISOString()
    } else {
      values[field.fieldKey] = text
    }
  }
  if (missing.length > 0) {
    formMessage.value = `请填写必填项：${missing.join('、')}`
    return null
  }
  return values
}

async function submitForm() {
  if (!props.technicianContextId || !detail.value || !activeForm.value || formSubmitting.value) return
  if (unsupportedFormReasons.value.length > 0) return
  if (formConditionState.value.errors.length > 0) {
    formMessage.value = `条件求值失败，已阻止提交：${formConditionState.value.errors.join('；')}`
    return
  }
  if (formConditionState.value.ruleFailures.length > 0) {
    formMessage.value = `跨字段规则未通过：${formConditionState.value.ruleFailures.join('；')}`
    return
  }
  const values = submissionValues()
  if (!values) return
  formSubmitting.value = true
  formIssues.value = []
  formMessage.value = '正在提交不可变表单事实…'
  try {
    const response = await submitTechnicianTaskForm(
      props.technicianContextId, detail.value.taskId, activeForm.value.formVersionId, values,
    )
    const issues = response.data.errors
    const message = response.data.validationStatus === 'VALIDATED'
      ? `表单提交成功（版本 ${response.data.submissionVersion}）`
      : '服务器已保留本次 INVALID 提交；请按错误修正后产生新版本'
    await load()
    formIssues.value = issues
    formMessage.value = message
  } catch (err) {
    formMessage.value = userFacingError(err, '表单提交失败，请刷新任务后重试')
  } finally {
    formSubmitting.value = false
  }
}

async function loadForms(taskId: string) {
  taskForms.value = []
  formValues.value = {}
  formIssues.value = []
  formMessage.value = null
  if (!props.technicianContextId || detail.value?.formSubmissions === null) return
  formLoading.value = true
  try {
    taskForms.value = await listTechnicianTaskForms(props.technicianContextId, taskId)
    const initial: Record<string, string | boolean> = {}
    for (const field of taskForms.value[0]?.definition.sections.flatMap((section) => section.fields) ?? []) {
      initial[field.fieldKey] = field.dataType === 'BOOLEAN' ? false : ''
    }
    formValues.value = initial
  } catch (err) {
    formMessage.value = userFacingError(err, '任务表单加载失败')
  } finally {
    formLoading.value = false
  }
}

function evidenceAccept(slot: TechnicianEvidenceSlot) {
  if (slot.mediaType === 'PHOTO') return 'image/*'
  if (slot.mediaType === 'VIDEO') return 'video/*'
  if (slot.mediaType === 'DOCUMENT') return 'image/*,application/pdf'
  return '*/*'
}

function selectEvidenceFile(slotId: string, event: Event) {
  selectedEvidenceFiles.value[slotId] = (event.target as HTMLInputElement).files?.[0]
}

function itemsForSlot(slotId: string) {
  return evidenceItems.value.filter((item) => item.evidenceSlotId === slotId)
}

async function uploadEvidence(slot: TechnicianEvidenceSlot) {
  const context = props.technicianContextId
  const task = detail.value
  const file = selectedEvidenceFiles.value[slot.slotId]
  if (!context || !task || !file || evidenceUploadingSlotId.value) return
  if (!slot.active || slot.requiredDisposition !== 'NONE') {
    evidenceMessage.value = '该资料槽位当前不可上传，请先完成条件变化处置'
    return
  }
  evidenceUploadingSlotId.value = slot.slotId
  evidenceMessage.value = '正在计算 SHA-256…'
  try {
    const digest = await sha256Hex(file)
    const existing = itemsForSlot(slot.slotId)
    // 未达到 maxCount 时创建新 Item；达到上限后在最后一个 Item 上追加 Revision，不能突破槽位上限。
    const evidenceItemId = slot.maxCount !== null && existing.length >= slot.maxCount
      ? existing.at(-1)?.evidenceItemId ?? null
      : null
    evidenceMessage.value = '正在创建受限上传会话…'
    const session = (await beginTechnicianEvidenceUpload(context, task.taskId, slot.slotId, {
      evidenceItemId,
      originalFileName: file.name || `evidence-${crypto.randomUUID()}`,
      declaredMimeType: file.type || 'application/octet-stream',
      expectedSize: file.size,
      expectedSha256: digest,
      captureSource: 'FILE',
      capturedAt: new Date(file.lastModified || Date.now()).toISOString(),
    })).data
    evidenceMessage.value = '正在上传到受限私有地址…'
    await putTechnicianEvidenceUpload(session, file)
    evidenceMessage.value = '正在由服务器校验并 Finalize…'
    const item = (await finalizeTechnicianEvidenceUpload(
      context, task.taskId, slot.slotId, session.uploadSessionId, digest,
    )).data
    const revision = item.revisions.at(-1)
    evidenceMessage.value = `资料已 Finalize：Item ${item.itemOrdinal} / Revision ${revision?.revisionNumber ?? '—'}，等待扫描与机器校验`
    selectedEvidenceFiles.value[slot.slotId] = undefined
    await loadEvidence(task.taskId)
  } catch (err) {
    evidenceMessage.value = userFacingError(err, '资料上传失败，请重新选择文件后重试')
  } finally {
    evidenceUploadingSlotId.value = null
  }
}

async function loadEvidence(taskId: string) {
  evidenceSlots.value = []
  evidenceItems.value = []
  evidenceLoading.value = true
  try {
    if (!props.technicianContextId) return
    const [slots, items] = await Promise.all([
      listTechnicianTaskEvidenceSlots(props.technicianContextId, taskId),
      listTechnicianTaskEvidenceItems(props.technicianContextId, taskId),
    ])
    evidenceSlots.value = slots
    evidenceItems.value = items
  } catch (err) {
    evidenceMessage.value = userFacingError(err, '任务资料加载失败')
  } finally {
    evidenceLoading.value = false
  }
}

/**
 * READY → CLAIMED → RUNNING：先 claim（If-Match=当前 resourceVersion），
 * 再用 claim 回执里的新版本 start；start 的乐观锁版本不能用旧的 resourceVersion。
 */
async function claimAndStart() {
  const task = detail.value
  if (!task || taskClaimStarting.value) return
  taskClaimStarting.value = true
  taskClaimMessage.value = '正在接单…'
  let claimed = false
  try {
    const claimReceipt = await claimMainTask(task.taskId, task.resourceVersion)
    claimed = true
    taskClaimMessage.value = '接单成功，正在开工…'
    await startMainTask(task.taskId, claimReceipt.data.version)
    taskClaimMessage.value = '已开工，任务进入执行中'
    await load()
  } catch (err) {
    taskClaimMessage.value = userFacingError(err, '接单开工失败，请刷新任务状态后重试')
    if (claimed) {
      // claim 已生效但 start 失败：刷新后按 CLAIMED 展示“开始任务”重试入口
      await load()
    }
  } finally {
    taskClaimStarting.value = false
  }
}

/** CLAIMED → RUNNING：If-Match 用当前详情 resourceVersion。 */
async function startTask() {
  const task = detail.value
  if (!task || taskClaimStarting.value) return
  taskClaimStarting.value = true
  taskClaimMessage.value = '正在开工…'
  try {
    await startMainTask(task.taskId, task.resourceVersion)
    taskClaimMessage.value = '已开工，任务进入执行中'
    await load()
  } catch (err) {
    taskClaimMessage.value = userFacingError(err, '开工失败，请刷新任务状态后重试')
  } finally {
    taskClaimStarting.value = false
  }
}

async function completeTask() {
  const context = props.technicianContextId
  const task = detail.value
  if (!context || !task || taskSubmitting.value) return
  const revisionIds = evidenceItems.value
    .map((item) => item.revisions.filter((revision) => revision.status === 'VALIDATED')
      .sort((left, right) => right.revisionNumber - left.revisionNumber)[0]?.evidenceRevisionId)
    .filter((id): id is string => Boolean(id))
  const formSubmissionId = activeForm.value
    ? task.formSubmissions?.filter((submission) => submission.validationStatus === 'VALIDATED')
      .sort((left, right) => right.submissionVersion - left.submissionVersion)[0]?.submissionId ?? null
    : null
  if (revisionIds.length === 0) {
    taskSubmissionMessage.value = '没有可冻结的 VALIDATED 资料版本；请等待服务器扫描与机器校验'
    return
  }
  if (activeForm.value && !formSubmissionId) {
    taskSubmissionMessage.value = '当前表单尚无 VALIDATED 提交，不能完成任务'
    return
  }
  taskSubmitting.value = true
  taskSubmissionMessage.value = '正在冻结不可变资料集合…'
  try {
    const snapshot = await createTechnicianTaskEvidenceSetSnapshot(context, task.taskId, revisionIds)
    taskSubmissionMessage.value = '资料集合已冻结，正在由服务器复核双输入并完成任务…'
    await completeTechnicianTask(
      context, task.taskId, task.resourceVersion, snapshot.data.evidenceSetSnapshotId, formSubmissionId,
    )
    taskSubmissionMessage.value = '任务已由服务器完成；Snapshot 与输入版本引用均已冻结'
    await load()
  } catch (err) {
    taskSubmissionMessage.value = userFacingError(err, '任务提交失败，请刷新任务状态后重试')
  } finally {
    taskSubmitting.value = false
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
    await Promise.all([loadForms(taskId), loadEvidence(taskId)])
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
  <section data-testid="technician-portal-task-detail" data-page-id="TECHNICIAN.TASK.DETAIL" class="detail-page">
    <header class="top">
      <div>
        <RouterLink to="/technician-portal/task-feed">← 返回今日任务</RouterLink>
        <p class="eyebrow">任务详情</p>
        <h2>{{ detail?.taskType ? statusLabel(detail.taskType) : '当前任务' }}</h2>
        <p class="hint">完成当前步骤所需的最小信息；下一步动作固定在底部。</p>
      </div>
      <button type="button" data-testid="technician-task-detail-refresh" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error" data-testid="technician-task-detail-error">{{ error }}</p>
    <template v-else-if="detail">
      <section class="next-step" data-testid="technician-task-next-step">
        <p>
          当前状态 <strong>{{ statusLabel(detail.taskStatus) }}</strong>
          · 阶段 {{ statusLabel(detail.stageCode) }}
        </p>
        <p class="hint">
          当前步骤：<strong>{{ currentWorkStep.label }}</strong>
          ·
          {{
            detail.executionGuarded
              ? '执行已保护，请先解除阻塞后再操作。'
              : currentWorkStep.key === 'appointment'
                ? '请先确认预约安排，再进入现场。'
                : currentWorkStep.key === 'checkin'
                  ? '到达现场后进行到场签到。'
                  : currentWorkStep.key === 'form'
                    ? '按冻结表单完成现场填写。'
                    : currentWorkStep.key === 'evidence'
                      ? '上传必需资料并等待服务器校验。'
                      : '完成提交前检查后冻结资料并提交。'
          }}
        </p>
        <ol class="work-steps" data-testid="technician-work-steps" aria-label="作业步骤">
          <li
            v-for="step in workSteps"
            :key="step.key"
            :data-status="step.status"
            :data-testid="`technician-work-step-${step.key}`"
          >
            {{ step.label }}
            <span v-if="step.status === 'current'" class="badge">当前</span>
          </li>
        </ol>
      </section>
      <dl class="summary" data-testid="technician-task-detail-summary">
        <div><dt>任务编号</dt><dd data-testid="technician-task-detail-task-id">{{ detail.taskId }}</dd></div>
        <div><dt>关联工单</dt><dd>{{ detail.workOrderId }}</dd></div>
        <div><dt>所属项目</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
        <div><dt>状态</dt><dd data-testid="technician-task-detail-status">{{ statusLabel(detail.taskStatus) }}</dd></div>
        <div><dt>阶段</dt><dd>{{ statusLabel(detail.stageCode) }}</dd></div>
        <div><dt>任务类型</dt><dd>{{ statusLabel(detail.taskType) }} / {{ statusLabel(detail.taskKind) }}</dd></div>
        <div><dt>业务类型</dt><dd>{{ detail.businessType ? statusLabel(detail.businessType) : '—' }}</dd></div>
        <div><dt>执行保护</dt><dd>{{ detail.executionGuarded ? '已保护，暂不可执行' : '未保护' }}</dd></div>
        <div><dt>资源版本</dt><dd>{{ detail.resourceVersion }}</dd></div>
        <div><dt>统计时间</dt><dd>{{ formatDateTime(detail.asOf) }}</dd></div>
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
              <td>{{ statusLabel(appointment.type) }}</td>
              <td>{{ statusLabel(appointment.status) }}</td>
              <td>{{ formatDateTime(appointment.windowStart) }}</td>
              <td>{{ formatDateTime(appointment.windowEnd) }}</td>
              <td>{{ appointment.timezone ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else>暂无预约</p>
      </section>

      <section class="visits" data-testid="technician-work-section-checkin">
        <div class="section-title">
          <h3>到场签到</h3>
          <span>不含定位与设备明细</span>
        </div>
        <table v-if="detail.visits.length > 0" data-testid="technician-task-detail-visits">
          <thead>
            <tr><th>序次</th><th>状态</th><th>到场</th><th>围栏</th><th>策略</th><th>结果/异常</th><th>版本</th></tr>
          </thead>
          <tbody>
            <tr v-for="visit in detail.visits" :key="visit.visitId">
              <td>{{ visit.visitSequence }}</td>
              <td>{{ statusLabel(visit.status) }}</td>
              <td>{{ formatDateTime(visit.checkInCapturedAt) }}</td>
              <td>{{ statusLabel(visit.geofenceResult) }}</td>
              <td>{{ statusLabel(visit.policyDecision) }}</td>
              <td>{{ statusLabel(visit.resultCode ?? visit.exceptionCode) }}</td>
              <td>{{ visit.aggregateVersion }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-visits">暂无上门记录</p>

        <div class="visit-actions" data-testid="technician-visit-online-actions">
          <h4>在线现场操作</h4>
          <p class="hint">
            到场需要一次用户授权的浏览器定位；H5 不持续跟踪、不后台定位，也不宣称具备原生可信度。
          </p>
          <button
            type="button"
            :disabled="visitActionBusy || !confirmedAppointment || Boolean(activeVisit) || detail.executionGuarded"
            data-testid="technician-visit-check-in"
            @click="checkIn"
          >
            {{ visitActionBusy ? '处理中…' : '到场签到' }}
          </button>
          <form v-if="activeVisit" class="interrupt-form" data-testid="technician-visit-interrupt-form" @submit.prevent="interrupt">
            <label>
              无法施工原因
              <select v-model="interruptCode" data-testid="technician-visit-interrupt-code">
                <option value="CUSTOMER_NOT_HOME">客户不在</option>
                <option value="SITE_NOT_READY">现场不具备施工条件</option>
                <option value="SAFETY_RISK">安全风险</option>
                <option value="OTHER">其他</option>
              </select>
            </label>
            <label>
              说明（可选）
              <textarea v-model="interruptNote" maxlength="500" data-testid="technician-visit-interrupt-note" />
            </label>
            <button type="submit" :disabled="visitActionBusy" data-testid="technician-visit-interrupt">
              记录无法施工
            </button>
          </form>
          <p v-if="activeVisit" class="hint" data-testid="technician-visit-checkout-boundary">
            离场签退需原生 FieldOperation 能力；H5 当前不提供伪造签退。
          </p>
          <p v-if="visitActionMessage" class="action-message" role="status" data-testid="technician-visit-action-message">
            {{ visitActionMessage }}
          </p>
        </div>
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
              <td>{{ statusLabel(submission.validationStatus) }}</td>
              <td>{{ submission.errorCount }}</td>
              <td>{{ submission.warningCount }}</td>
              <td>{{ formatDateTime(submission.submittedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-form-submissions">暂无表单提交</p>

        <div class="online-form" data-testid="technician-online-form">
          <h4>在线填写冻结表单</h4>
          <p class="hint">输入仅保存在当前页面内存；草稿与 prefill 冲突策略未接受，不会伪装成已保存草稿。</p>
          <p v-if="formLoading">正在加载冻结表单…</p>
          <p v-else-if="detail.formSubmissions === null">当前上下文无表单读取权限</p>
          <p v-else-if="!activeForm" data-testid="technician-online-form-empty">当前任务未锁定表单</p>
          <template v-else>
            <p><strong>{{ activeForm.definition.title ?? activeForm.formKey }}</strong> · {{ activeForm.semanticVersion }}</p>
            <div v-if="unsupportedFormReasons.length > 0" class="error" data-testid="technician-online-form-unsupported">
              当前表单不能由本客户端安全执行：{{ unsupportedFormReasons.join('；') }}。已阻止提交。
            </div>
            <form v-else class="dynamic-form" data-testid="technician-online-form-fields" @submit.prevent="submitForm">
              <p
                v-if="formConditionState.errors.length > 0"
                class="error"
                data-testid="technician-online-form-condition-error"
              >
                条件求值失败（失败关闭）：{{ formConditionState.errors.join('；') }}
              </p>
              <p
                v-if="formConditionState.ruleFailures.length > 0"
                class="error"
                data-testid="technician-online-form-rule-failures"
              >
                跨字段规则未通过：{{ formConditionState.ruleFailures.join('；') }}
              </p>
              <fieldset
                v-for="section in activeForm.definition.sections"
                v-show="isSectionVisible(section.sectionKey)"
                :key="section.sectionKey"
                :data-testid="`technician-form-section-${section.sectionKey}`"
              >
                <legend>{{ section.title }}</legend>
                <label
                  v-for="field in section.fields"
                  v-show="isFieldVisible(field.fieldKey)"
                  :key="field.fieldKey"
                >
                  <span>{{ field.label }}<em v-if="isFieldRequired(field)"> *</em></span>
                  <input
                    v-if="field.dataType === 'BOOLEAN'"
                    v-model="formValues[field.fieldKey]"
                    type="checkbox"
                    :data-testid="`technician-form-field-${field.fieldKey}`"
                  >
                  <textarea
                    v-else-if="field.dataType === 'TEXT'"
                    :value="String(formValues[field.fieldKey] ?? '')"
                    :data-testid="`technician-form-field-${field.fieldKey}`"
                    @input="setTextValue(field.fieldKey, $event)"
                  />
                  <input
                    v-else
                    :value="String(formValues[field.fieldKey] ?? '')"
                    :type="inputType(field)"
                    :step="field.dataType === 'DECIMAL' ? 'any' : undefined"
                    :data-testid="`technician-form-field-${field.fieldKey}`"
                    @input="setTextValue(field.fieldKey, $event)"
                  >
                </label>
              </fieldset>
              <button
                type="submit"
                :disabled="
                  formSubmitting
                    || detail.executionGuarded
                    || detail.taskStatus !== 'RUNNING'
                    || formConditionState.errors.length > 0
                    || formConditionState.ruleFailures.length > 0
                "
                data-testid="technician-online-form-submit"
              >{{ formSubmitting ? '提交中…' : '提交不可变表单' }}</button>
            </form>
          </template>
          <ul v-if="formIssues.length > 0" class="error" data-testid="technician-online-form-errors">
            <li v-for="issue in formIssues" :key="`${issue.fieldKey}-${issue.code}`">
              {{ issue.fieldKey }}：{{ issue.message }}
            </li>
          </ul>
          <p v-if="formMessage" role="status" data-testid="technician-online-form-message">{{ formMessage }}</p>
        </div>
      </section>

      <section class="evidence" data-testid="technician-online-evidence">
        <div class="section-title">
          <h3>在线现场资料</h3>
          <span>受限 PUT → Finalize → 隔离扫描</span>
        </div>
        <p class="hint">
          文件只在你明确选择后上传；浏览器端不缓存草稿、不后台重试，也不会把选中文件当作已提交资料。
        </p>
        <p v-if="evidenceLoading">正在加载资料槽位…</p>
        <p v-else-if="evidenceSlots.length === 0" data-testid="technician-evidence-empty">当前任务没有资料槽位</p>
        <div v-else class="evidence-slots">
          <article
            v-for="slot in evidenceSlots"
            :key="slot.slotId"
            class="evidence-slot"
            :data-testid="`technician-evidence-slot-${slot.slotId}`"
          >
            <div>
              <strong>{{ slot.requirementName }}</strong>
              <span>{{ slot.required ? '必需' : '可选' }} · {{ statusLabel(slot.mediaType) }} · {{ statusLabel(slot.status) }}</span>
            </div>
            <p>数量 {{ itemsForSlot(slot.slotId).length }} / {{ slot.maxCount ?? '不限' }}，最低 {{ slot.minCount }}</p>
            <ul v-if="itemsForSlot(slot.slotId).length > 0" class="revision-list">
              <li v-for="item in itemsForSlot(slot.slotId)" :key="item.evidenceItemId">
                Item {{ item.itemOrdinal }}：
                <span v-if="item.revisions.length > 0">
                  Revision {{ item.revisions.at(-1)?.revisionNumber }} · {{ statusLabel(item.revisions.at(-1)?.status) }}
                </span>
                <span v-else>尚无 Revision</span>
              </li>
            </ul>
            <p v-if="!slot.active || slot.requiredDisposition !== 'NONE'" class="error">
              条件变化待处置，当前客户端已阻止上传。
            </p>
            <template v-else>
              <input
                type="file"
                :accept="evidenceAccept(slot)"
                :capture="slot.mediaType === 'PHOTO' ? 'environment' : undefined"
                :disabled="evidenceUploadingSlotId !== null || detail.executionGuarded || detail.taskStatus !== 'RUNNING'"
                :data-testid="`technician-evidence-file-${slot.slotId}`"
                @change="selectEvidenceFile(slot.slotId, $event)"
              >
              <button
                type="button"
                :disabled="!selectedEvidenceFiles[slot.slotId] || evidenceUploadingSlotId !== null || detail.executionGuarded || detail.taskStatus !== 'RUNNING'"
                :data-testid="`technician-evidence-upload-${slot.slotId}`"
                @click="uploadEvidence(slot)"
              >
                {{ evidenceUploadingSlotId === slot.slotId ? '上传并校验中…' : '上传并 Finalize' }}
              </button>
            </template>
          </article>
        </div>
        <p v-if="evidenceMessage" role="status" data-testid="technician-evidence-message">{{ evidenceMessage }}</p>
      </section>

      <section class="task-submission" data-testid="technician-task-submission">
        <div class="section-title">
          <h3>提交前检查</h3>
          <span>Snapshot → 服务端复核 → Complete</span>
        </div>
        <div
          v-if="detail.taskStatus === 'READY' || detail.taskStatus === 'CLAIMED'"
          class="task-claim"
          data-testid="technician-task-claim-start"
        >
          <h4>{{ detail.taskStatus === 'READY' ? '接单开工' : '开始任务' }}</h4>
          <p class="hint">
            {{
              detail.taskStatus === 'READY'
                ? '任务尚未接单：接单并开工后才进入可执行状态，才能填写表单、上传资料与完成任务。'
                : '任务已由你接单；开工后才进入可执行状态。'
            }}
          </p>
          <button
            type="button"
            :disabled="taskClaimStarting || detail.executionGuarded"
            data-testid="technician-task-claim-start-button"
            @click="detail.taskStatus === 'READY' ? claimAndStart() : startTask()"
          >
            {{
              taskClaimStarting
                ? (detail.taskStatus === 'READY' ? '接单开工处理中…' : '开工处理中…')
                : (detail.taskStatus === 'READY' ? '接单开工' : '开始任务')
            }}
          </button>
          <p v-if="taskClaimMessage" role="status" data-testid="technician-task-claim-start-message">
            {{ taskClaimMessage }}
          </p>
        </div>
        <ul class="presubmit-checks" data-testid="technician-presubmit-checks">
          <li
            v-for="check in preSubmitChecks"
            :key="check.key"
            :data-ok="check.ok"
            :data-testid="`technician-presubmit-${check.key}`"
          >
            <span>{{ check.ok ? '已满足' : '未满足' }}</span>
            {{ check.label }}
          </li>
        </ul>
        <p class="hint">
          客户端只选择每个资料 Item 的最新 VALIDATED Revision；不可变引用、摘要和输入版本均由服务器重新读取并冻结。
        </p>
        <button
          type="button"
          :disabled="
            taskSubmitting
              || evidenceLoading
              || evidenceUploadingSlotId !== null
              || detail.executionGuarded
              || detail.taskStatus !== 'RUNNING'
              || !canCompleteTask
          "
          data-testid="technician-task-complete"
          @click="completeTask"
        >{{ taskSubmitting ? '服务器复核并完成中…' : '冻结资料并完成任务' }}</button>
        <p v-if="taskSubmissionMessage" role="status" data-testid="technician-task-submission-message">
          {{ taskSubmissionMessage }}
        </p>
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
              <td>{{ statusLabel(attempt.channel) }}</td>
              <td>{{ statusLabel(attempt.resultCode) }}</td>
              <td>{{ formatDateTime(attempt.startedAt) }}</td>
              <td>{{ formatDateTime(attempt.endedAt) }}</td>
              <td>{{ formatDateTime(attempt.nextContactAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-contact-attempts">暂无联系记录</p>
      </section>

      <p class="boundary" data-testid="technician-task-detail-boundary">
        任务详情摘要不返回地址、联系人、联系对象引用、录音、GPS、设备、离线命令、文件对象或提交人；在线表单只读取任务冻结定义，资料只返回安全状态摘要；均不提供草稿、后台恢复或离线工作包。
      </p>

      <footer class="sticky-action" data-testid="technician-sticky-action">
        <RouterLink
          v-if="stickyAction.kind === 'link'"
          class="primary"
          :to="{ path: stickyAction.to, query: { taskId: detail.taskId } }"
          data-testid="technician-sticky-action-button"
        >
          {{ stickyAction.label }}
        </RouterLink>
        <button
          v-else-if="stickyAction.kind !== 'disabled'"
          type="button"
          class="primary"
          :disabled="stickyAction.disabled"
          data-testid="technician-sticky-action-button"
          @click="onStickyAction"
        >
          {{ stickyAction.label }}
        </button>
        <p v-else class="muted" data-testid="technician-sticky-action-button">{{ stickyAction.label }}</p>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.detail-page {
  display: grid;
  gap: 12px;
  padding-bottom: 96px;
}
.work-steps {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.work-steps li {
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  background: #fff;
  color: var(--sos-color-text-secondary, #4b5563);
}
.work-steps li[data-status='done'] {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #389e0d;
}
.work-steps li[data-status='current'] {
  border-color: var(--sos-primary-600, #1677ff);
  background: var(--sos-primary-100, #e6f4ff);
  color: var(--sos-primary-800, #003eb3);
  font-weight: 600;
}
.work-steps li[data-status='blocked'] {
  border-color: #ffccc7;
  background: #fff2f0;
  color: #cf1322;
}
.work-steps .badge {
  margin-left: 4px;
  font-size: 11px;
  background: var(--sos-primary-600, #1677ff);
  color: #fff;
  border-radius: 999px;
  padding: 0 6px;
}
.presubmit-checks {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: grid;
  gap: 8px;
}
.presubmit-checks li {
  display: flex;
  gap: 8px;
  align-items: center;
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
  font-size: 14px;
}
.presubmit-checks li span {
  font-size: 12px;
  font-weight: 700;
  min-width: 48px;
}
.presubmit-checks li[data-ok='true'] {
  border-color: #b7eb8f;
  background: #f6ffed;
}
.presubmit-checks li[data-ok='false'] {
  border-color: #ffe58f;
  background: #fffbe6;
}
.sticky-action {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(64px + env(safe-area-inset-bottom, 0px));
  z-index: 25;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--sos-color-border-default, #e5e7eb);
}
.sticky-action .primary,
.sticky-action a.primary,
.sticky-action button.primary {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--sos-primary-600, #1677ff);
  border: 1px solid var(--sos-primary-600, #1677ff);
  color: #fff;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.sticky-action button.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (min-width: 900px) {
  .sticky-action {
    bottom: 0;
    left: calc(260px + 48px);
    right: 24px;
    border-radius: 12px 12px 0 0;
  }
}
.top,
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.eyebrow {
  margin: 0.35rem 0 0.2rem;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.next-step {
  border: 1px solid var(--sos-primary-500, #4096ff);
  background: var(--sos-primary-100, #e6f4ff);
  border-radius: 12px;
  padding: 12px;
}
.next-step p {
  margin: 0 0 4px;
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
.task-claim {
  margin: 0 0 12px;
  padding: 1rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}
.online-form {
  margin-top: 1rem;
  padding: 1rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}
.evidence-slots {
  display: grid;
  gap: 0.75rem;
}
.evidence-slot {
  display: grid;
  gap: 0.6rem;
  padding: 1rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}
.evidence-slot > div {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}
.revision-list {
  margin: 0;
}
.dynamic-form,
.dynamic-form fieldset,
.dynamic-form label {
  display: grid;
  gap: 0.75rem;
}
.dynamic-form fieldset {
  margin: 0 0 1rem;
  border: 1px solid #dbe3ec;
}
.dynamic-form input:not([type='checkbox']),
.dynamic-form textarea {
  min-height: 44px;
  font: inherit;
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
