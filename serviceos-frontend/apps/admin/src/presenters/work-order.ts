const stageLabels: Record<string, string> = {
  PILOT_INTAKE: '接单受理',
  PILOT_DISPATCH: '网点分配',
  PILOT_APPOINTMENT: '预约确认',
  SURVEY: '上门勘测',
  PILOT_SURVEY: '上门勘测',
  INSTALLATION: '上门安装',
  PILOT_INSTALLATION: '上门安装',
  FINAL_REVIEW: '资料审核',
  CLIENT_CALLBACK: '车企回传',
}

const taskLabels: Record<string, string> = {
  ASSIGN_COORDINATORS: '分配责任网点',
  FIELD_SURVEY: '上门勘测',
  FIELD_INSTALL: '上门安装',
  REVIEW_TASK: '资料审核',
  PILOT_SURVEY: '上门勘测',
  PILOT_INSTALLATION: '上门安装',
  NETWORK_ASSIGNMENT: '网点分配',
  TECHNICIAN_ASSIGNMENT: '师傅分配',
}

const serviceLabels: Record<string, string> = {
  HOME_CHARGER_INSTALLATION: '充电桩安装服务',
  HOME_CHARGER_SURVEY: '家充勘测服务',
  CHARGER_REPAIR: '充电桩维修服务',
}

function requireLabel(labels: Record<string, string>, code: string | null, field: string): string {
  if (!code) return '尚未进入'
  const label = labels[code]
  if (!label) throw new Error(`页面数据不完整：${field} 缺少中文名称（${code}）`)
  return label
}

export const stageLabel = (code: string | null) => requireLabel(stageLabels, code, '当前阶段')
export const taskLabel = (code: string | null) => requireLabel(taskLabels, code, '当前任务')
export const serviceLabel = (code: string) => requireLabel(serviceLabels, code, '服务产品')

export function clientLabel(code: string): string {
  const labels: Record<string, string> = { BYD: '比亚迪', GEELY: '吉利汽车', NIO: '蔚来汽车' }
  return requireLabel(labels, code, '客户品牌')
}

const workOrderStatusLabels: Record<string, string> = {
  RECEIVED: '待受理',
  ACTIVE: '进行中',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
}

export const workOrderStatusLabel = (code: string) =>
  requireLabel(workOrderStatusLabels, code, '工单状态')

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

/**
 * 已知码映射中文、未知码原样返回英文码。
 * 用于时间线事件、区块状态等可扩展枚举：后端会持续合并新值，页面不得因未知码失败关闭。
 */
function labelOrCode(labels: Record<string, string>, code: string | null | undefined): string {
  if (!code) return '—'
  return labels[code] ?? code
}

/** 时间线事件中文映射；覆盖工单/任务/预约/上门/资料/审核/回传常见事件，未知事件原样显示英文码。 */
const timelineEventLabels: Record<string, string> = {
  'workorder.received': '工单已接收',
  'workorder.activated': '工单已激活',
  'workorder.fulfilled': '工单已完成',
  'workorder.cancelled': '工单已取消',
  'workorder.reopened': '工单已重开',
  'workorder.external-details-updated': '车企信息已更新',
  'workflow.started': '履约流程已启动',
  'workflow.completed': '履约流程已完成',
  'stage.activated': '阶段已开始',
  'stage.completed': '阶段已完成',
  'task.created': '任务已创建',
  'task.claimed': '任务已领取',
  'task.started': '任务已启动',
  'task.released': '任务已释放',
  'task.cancelled': '任务已取消',
  'task.completed': '任务已完成',
  'task.assigned': '任务已指派',
  'task.assignment-prepared': '任务指派已就绪',
  'task.assignment-activated': '任务指派已生效',
  'task.assignment-aborted': '任务指派已中止',
  'appointment.proposed': '预约已提议',
  'appointment.confirmed': '预约已确认',
  'appointment.rescheduled': '预约已改期',
  'appointment.cancelled': '预约已取消',
  'appointment.no-show-marked': '已标记客户未到场',
  'contact.attempt.recorded': '已记录联系尝试',
  'visit.checked-in': '师傅已签到',
  'visit.checked-out': '师傅已签退',
  'visit.interrupted': '上门已中断',
  'sla.started': 'SLA 已启动',
  'sla.breached': 'SLA 已超时',
  'sla.met': 'SLA 已达成',
  'form.submitted': '表单已提交',
  'evidence.set-snapshotted': '资料集合已冻结',
  'evidence.review-case-created': '平台审核单已创建',
  'evidence.client-review-case-created': '车企审核单已创建',
  'evidence.review-decided': '审核已裁决',
  'evidence.review-case-reopened': '审核单已重开',
  'evidence.correction-case-created': '整改单已创建',
  'evidence.correction-resubmitted': '整改已重新提交',
  'evidence.correction-closed': '整改单已关闭',
  'evidence.correction-waived': '整改已豁免',
  'evidence.external-review-receipt-recorded': '车企审核回执已登记',
  'integration.outbound-delivery-created': '出站回传已创建',
  'integration.outbound-delivery-acknowledged': '出站回传已被车企确认',
  'integration.outbound-delivery-recovered': '出站回传已恢复',
  'integration.outbound-delivery-replay-requested': '出站回传已请求重放',
  'service.assignment.activated': '履约责任已激活',
  'service.assignment.activation-completed': '履约责任激活完成',
  'service.assignment.activation-aborted': '履约责任激活已中止',
  'operational.exception.acknowledged': '异常已受理',
  'operational.exception.resolved': '异常已解决',
}

export const timelineEventLabel = (code: string | null | undefined) =>
  labelOrCode(timelineEventLabels, code)

const reviewStatusLabels: Record<string, string> = {
  OPEN: '待审核',
  APPROVED: '审核通过',
  REJECTED: '审核驳回',
  FORCE_APPROVED: '强制通过',
  REOPENED: '已重开',
}

export const reviewStatusLabel = (code: string | null | undefined) =>
  labelOrCode(reviewStatusLabels, code)

const reviewDecisionLabels: Record<string, string> = {
  APPROVED: '通过',
  REJECTED: '驳回',
  FORCE_APPROVED: '强制通过',
}

export const reviewDecisionLabel = (code: string | null | undefined) =>
  labelOrCode(reviewDecisionLabels, code)

const reviewOriginLabels: Record<string, string> = {
  INTERNAL: '平台审核',
  CLIENT: '车企审核',
}

export const reviewOriginLabel = (code: string | null | undefined) =>
  labelOrCode(reviewOriginLabels, code)

const correctionStatusLabels: Record<string, string> = {
  OPEN: '待整改',
  IN_PROGRESS: '整改中',
  RESUBMITTED: '已重新提交',
  CLOSED: '已关闭',
  WAIVED: '已豁免',
}

export const correctionStatusLabel = (code: string | null | undefined) =>
  labelOrCode(correctionStatusLabels, code)

const evidenceItemStatusLabels: Record<string, string> = {
  OPEN: '待提交',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  ACCEPTED: '已受理',
  REJECTED: '已驳回',
  LOCKED: '已锁定',
}

export const evidenceItemStatusLabel = (code: string | null | undefined) =>
  labelOrCode(evidenceItemStatusLabels, code)

const evidenceRevisionStatusLabels: Record<string, string> = {
  STORED: '已存储',
  VALIDATING: '校验中',
  VALIDATED: '校验通过',
  VALIDATION_FAILED: '校验失败',
  QUARANTINED: '已隔离',
  INVALIDATED: '已作废',
}

export const evidenceRevisionStatusLabel = (code: string | null | undefined) =>
  labelOrCode(evidenceRevisionStatusLabels, code)

const evidenceMediaTypeLabels: Record<string, string> = {
  PHOTO: '照片',
  VIDEO: '视频',
  DOCUMENT: '文档',
  SIGNATURE: '签名',
  GENERATED_REPORT: '生成报告',
}

export const evidenceMediaTypeLabel = (code: string | null | undefined) =>
  labelOrCode(evidenceMediaTypeLabels, code)

const appointmentTypeLabels: Record<string, string> = {
  SURVEY: '上门勘测',
  INSTALLATION: '上门安装',
  REPAIR: '维修',
  CORRECTION: '整改',
  SECOND_VISIT: '二次上门',
}

export const appointmentTypeLabel = (code: string | null | undefined) =>
  labelOrCode(appointmentTypeLabels, code)

const appointmentStatusLabels: Record<string, string> = {
  PROPOSED: '待确认',
  CONFIRMED: '已确认',
  RESCHEDULED: '已改期',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
  NO_SHOW: '客户未到场',
}

export const appointmentStatusLabel = (code: string | null | undefined) =>
  labelOrCode(appointmentStatusLabels, code)

const visitStatusLabels: Record<string, string> = {
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  INTERRUPTED: '已中断',
}

export const visitStatusLabel = (code: string | null | undefined) =>
  labelOrCode(visitStatusLabels, code)

const deliveryStatusLabels: Record<string, string> = {
  PENDING: '待发送',
  SENDING: '发送中',
  DELIVERED: '已送达',
  ACKNOWLEDGED: '已确认',
  REJECTED: '被拒收',
  FAILED_FINAL: '最终失败',
  UNKNOWN: '状态未知',
}

export const deliveryStatusLabel = (code: string | null | undefined) =>
  labelOrCode(deliveryStatusLabels, code)

/** 审核/整改原因码中文映射；未知原因码原样显示英文码。 */
const reviewReasonCodeLabels: Record<string, string> = {
  PHOTO_BLURRY: '照片模糊',
  PHOTO_MISSING: '缺少必传照片',
  INFO_MISMATCH: '信息不一致',
  OTHER: '其他',
}

export const reviewReasonCodeLabel = (code: string | null | undefined) =>
  labelOrCode(reviewReasonCodeLabels, code)

export const reviewReasonCodeText = (codes: string[]) =>
  codes.map((code) => reviewReasonCodeLabel(code)).join('、')
