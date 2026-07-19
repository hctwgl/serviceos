/**
 * 状态枚举统一中文映射。同一状态在所有页面必须使用相同中文名。
 * 未知枚举回退为「未知状态（CODE）」，避免白屏，也不伪装成功。
 */
const STATUS_LABELS: Record<string, string> = {
  // 工单
  RECEIVED: '已接收',
  ACTIVE: '处理中',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
  SUSPENDED: '已挂起',
  CLOSED: '已关闭',
  // 通用任务/整改/审核
  PENDING: '待处理',
  READY: '待开始',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  ASSIGNED: '已分配',
  UNASSIGNED: '待分配',
  WAITING_REVIEW: '待审核',
  WAITING_CORRECTION: '待整改',
  RESUBMITTED: '已重新提交',
  OPEN: '待处理',
  WAIVED: '已豁免',
  CLOSED_OK: '已关闭',
  // 审核结论
  APPROVED: '已通过',
  FORCE_APPROVED: '强制通过',
  REOPENED: '已重开',
  // 任务执行
  CLAIMED: '已认领',
  RETRY_WAIT: '等待重试',
  MANUAL_INTERVENTION: '需人工介入',
  // 异常处置
  ACKNOWLEDGED: '已确认',
  RESOLVED: '已解决',
  // SLA
  RUNNING: '计时中',
  BREACHED: '已超时',
  MET: '已达标',
  MET_LATE: '逾期达标',
  // 分配
  PREPARING: '准备中',
  CONFIRMED: '已确认',
  // Visit / Appointment
  CHECKED_IN: '已签到',
  INTERRUPTED: '已中断',
  NO_SHOW: '未履约',
  PROPOSED: '已提出',
  // 入站/外发
  UNKNOWN: '未知',
  MAPPED: '已映射',
  FAILED: '失败',
  SUCCEEDED: '成功',
  DELIVERED: '已送达',
  RETRYING: '重试中',
}

/** 状态语义色：success / warning / danger / info / neutral */
export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const STATUS_TONES: Record<string, StatusTone> = {
  FULFILLED: 'success',
  COMPLETED: 'success',
  APPROVED: 'success',
  MET: 'success',
  FORCE_APPROVED: 'success',
  ACTIVE: 'info',
  IN_PROGRESS: 'info',
  RUNNING: 'info',
  ASSIGNED: 'info',
  CONFIRMED: 'info',
  PENDING: 'warning',
  READY: 'warning',
  OPEN: 'warning',
  RECEIVED: 'warning',
  WAITING_REVIEW: 'warning',
  WAITING_CORRECTION: 'warning',
  RESUBMITTED: 'warning',
  PROPOSED: 'warning',
  UNASSIGNED: 'warning',
  REJECTED: 'danger',
  CANCELLED: 'danger',
  MANUAL_INTERVENTION: 'danger',
  REOPENED: 'warning',
  CLAIMED: 'info',
  RETRY_WAIT: 'warning',
  ACKNOWLEDGED: 'info',
  RESOLVED: 'success',
  BREACHED: 'danger',
  FAILED: 'danger',
  NO_SHOW: 'danger',
  INTERRUPTED: 'danger',
  SUSPENDED: 'neutral',
  CLOSED: 'neutral',
  WAIVED: 'neutral',
  MET_LATE: 'warning',
}

export function statusLabel(code: string | null | undefined): string {
  if (code == null || code === '') {
    return '—'
  }
  const normalized = code.trim().toUpperCase()
  return STATUS_LABELS[normalized] ?? `未知状态（${code}）`
}

export function statusTone(code: string | null | undefined): StatusTone {
  if (code == null || code === '') {
    return 'neutral'
  }
  return STATUS_TONES[code.trim().toUpperCase()] ?? 'neutral'
}

export function statusOptions(
  codes: string[],
): Array<{ value: string; label: string }> {
  return codes.map((value) => ({ value, label: statusLabel(value) }))
}
